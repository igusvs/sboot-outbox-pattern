# Outbox Pattern com Spring Boot, MySQL e SQS

Projeto de estudo que implementa o **Transactional Outbox Pattern** para publicar eventos de criação de pedidos em uma fila **Amazon SQS** (via LocalStack), garantindo consistência entre o banco de dados e o sistema de mensageria.

## Sumário

- [O problema](#o-problema)
- [A solução: Outbox Pattern](#a-solução-outbox-pattern)
- [Benefícios](#benefícios)
- [Arquitetura do projeto](#arquitetura-do-projeto)
- [Estrutura de pastas](#estrutura-de-pastas)
- [Stack](#stack)
- [Como executar](#como-executar)
- [Configurações](#configurações)
- [Rodando em várias instâncias (pods)](#rodando-em-várias-instâncias-pods)
- [Garantias e limitações](#garantias-e-limitações)
- [Próximos passos](#próximos-passos)

---

## O problema

Em um sistema orientado a eventos, é comum precisar **salvar algo no banco** e **avisar outros serviços** que isso aconteceu. A abordagem ingênua é fazer as duas coisas em sequência:

```java
@Transactional
public void criarPedido() {
    pedidoRepository.save(pedido);   // 1. grava no MySQL
    sqsTemplate.send(pedido);        // 2. publica no SQS
}
```

Isso é o chamado **dual write** e tem falhas graves, porque banco e fila são sistemas diferentes e não participam da mesma transação:

| Cenário | Resultado |
|---|---|
| Salvou no banco, SQS fora do ar | Pedido existe, mas **ninguém é avisado** (evento perdido) |
| Publicou no SQS, commit do banco falhou | Consumidores processam um pedido que **não existe** |
| Aplicação caiu entre os dois passos | Estado inconsistente, sem como saber o que faltou |

## A solução: Outbox Pattern

Em vez de publicar direto na fila, a aplicação grava o evento numa tabela **`outbox`** **na mesma transação** do dado de negócio. Um processo separado (o *relay*) lê essa tabela e publica os eventos na fila.

```
┌──────────────────────── Transação única (MySQL) ────────────────────────┐
│                                                                          │
│   PedidoService.criarPedido()                                            │
│        ├── INSERT INTO pedidos (...)                                     │
│        └── INSERT INTO outbox  (aggregate_id, status = 'PENDING')        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼  (assíncrono, a cada N segundos)
┌──────────────────────────────────────────────────────────────────────────┐
│   OutboxRelay (@Scheduled)                                               │
│        1. SELECT ... WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED     │
│        2. Publica cada pedido no SQS (PublishService)                    │
│        3. UPDATE outbox SET status = 'SENT'                              │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
                        ┌──────────────────────┐
                        │  SQS: outbox-events  │ ──► consumidores
                        └──────────────────────┘
                                   │ (após 3 falhas de consumo)
                                   ▼
                        ┌──────────────────────┐
                        │ SQS: outbox-events-dlq│
                        └──────────────────────┘
```

Como o pedido e o evento são gravados atomicamente, **ou os dois existem, ou nenhum existe**. Se o SQS estiver fora do ar, o evento simplesmente fica `PENDING` e é publicado quando a fila voltar.

## Benefícios

- **Consistência entre banco e mensageria**: elimina o problema do dual write. Nenhum evento é perdido e nenhum evento "fantasma" é publicado.
- **Resiliência a falhas do broker**: indisponibilidade do SQS não derruba a criação de pedidos. Os eventos se acumulam na tabela e são enviados depois.
- **Desacoplamento**: a regra de negócio (`PedidoService`) não conhece a fila. Ela só grava no banco; quem fala com o SQS é o relay.
- **Retry natural**: eventos que falharam continuam `PENDING` e são tentados novamente na próxima execução, sem lógica extra no código de negócio.
- **Auditoria e rastreabilidade**: a tabela `outbox` funciona como histórico do que foi publicado, quando e com qual status.
- **Escalabilidade horizontal**: com `FOR UPDATE SKIP LOCKED`, várias instâncias da aplicação dividem os eventos entre si sem processar o mesmo evento ao mesmo tempo.
- **Sem transação distribuída (2PC/XA)**: consegue consistência usando apenas uma transação local do banco, que é simples, rápida e suportada por qualquer banco relacional.

## Arquitetura do projeto

| Componente | Responsabilidade |
|---|---|
| `PedidoDomain` | Record imutável que representa o pedido. Possui a factory `criar()` e validação no construtor compacto. |
| `PedidoService` | Cria o pedido e grava `pedidos` + `outbox` na mesma transação (`@Transactional`). **Não** acessa o SQS. |
| `PedidoEntity` / `OutboxEntity` | Mapeamento JPA das tabelas `pedidos` e `outbox`. |
| `OutboxRepository` | Busca eventos pendentes com `FOR UPDATE SKIP LOCKED`. |
| `OutboxRelay` | Job `@Scheduled` que lê eventos `PENDING`, publica no SQS e marca como `SENT`. |
| `PublishService` | Envia o `PedidoDomain` para a fila via `SqsTemplate` (serializado como JSON). |
| `PedidoGenerator` | Thread (virtual) iniciada quando a app sobe, que cria pedidos em loop para testes. |
| `SqsConfig` | Configura `SqsAsyncClient`, `SqsTemplate` (fila padrão) e o listener factory. |

### Modelo de dados

```sql
pedidos (pedido_id CHAR(36) PK, cliente_id VARCHAR(36), data_pedido DATE)

outbox  (id CHAR(36) PK, aggregate_id CHAR(36), status VARCHAR(20),
         created_at DATETIME(6), INDEX (status, created_at))
```

O `pedido_id` é um **UUID gerado na aplicação**. Assim o id já é conhecido antes do insert e o evento da outbox pode referenciá-lo na mesma transação.

### Exemplo de mensagem publicada

```json
{
  "idPedido": "3f2a9c1e-8b7d-4e2a-9f1c-0d6b5e4a3c21",
  "dataPedido": "2026-11-15",
  "idCliente": "184467"
}
```

## Estrutura de pastas

```
src/main/java/com/example/outboxpattern
├── OutboxPatternApplication.java   # @EnableScheduling
├── PedidoDomain.java               # record de domínio
├── config/
│   └── SqsConfig.java
├── entity/
│   ├── PedidoEntity.java
│   ├── OutboxEntity.java
│   └── OutboxStatus.java           # PENDING, SENT
├── repository/
│   ├── PedidoRepository.java
│   └── OutboxRepository.java
├── service/
│   ├── PedidoService.java          # escrita transacional (pedido + outbox)
│   └── PedidoGenerator.java        # gerador de carga para testes
└── queue/
    ├── OutboxRelay.java            # leitura da outbox + publicação
    └── PublishService.java         # envio ao SQS

docker/
├── mysql/init.sql                  # criação das tabelas
└── localstack/init-sqs.sh          # criação das filas (principal + DLQ)
```

## Stack

- Java 27
- Spring Boot 4.1 (Web MVC, Data JPA, Actuator)
- Spring Cloud AWS 4.2 (SQS)
- MySQL 8.4
- LocalStack (SQS local)
- Docker Compose

## Como executar

### Pré-requisitos

- JDK 27
- Docker e Docker Compose

### 1. Subir a infraestrutura

```bash
docker compose up -d
```

Isso sobe:
- **MySQL** em `localhost:3306` (banco `outbox`, usuário/senha `outbox`), já com as tabelas criadas pelo `init.sql`
- **LocalStack** em `localhost:4566`, com as filas `outbox-events` e `outbox-events-dlq` (redrive após 3 tentativas)

> O `init.sql` só é executado quando o volume do MySQL é criado. Se você alterou o schema, recrie o volume (**apaga os dados**):
> ```bash
> docker compose down -v && docker compose up -d
> ```

### 2. Rodar a aplicação

```bash
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

Com `outbox.generator.enabled=true`, a aplicação começa a criar um pedido por segundo, e o `OutboxRelay` publica os pendentes a cada 5 segundos.

### 3. Verificar

Eventos na tabela outbox:

```bash
docker exec -it outbox-mysql mysql -uoutbox -poutbox outbox \
  -e "SELECT status, COUNT(*) FROM outbox GROUP BY status;"
```

Mensagens na fila:

```bash
docker exec -it outbox-localstack awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/outbox-events \
  --max-number-of-messages 10
```

## Configurações

`src/main/resources/application.properties`:

| Propriedade | Padrão | Descrição |
|---|---|---|
| `outbox.sqs.queue-name` | `outbox-events` | Fila padrão usada pelo `SqsTemplate` |
| `outbox.relay.delay-ms` | `5000` | Intervalo entre execuções do relay |
| `outbox.generator.enabled` | `true` | Liga/desliga o gerador de pedidos de teste |
| `outbox.generator.interval-ms` | `1000` | Intervalo entre pedidos gerados |
| `spring.cloud.aws.endpoint` | `http://localhost:4566` | Endpoint do LocalStack (remova para usar a AWS real) |

## Rodando em várias instâncias (pods)

O relay usa `SELECT ... FOR UPDATE SKIP LOCKED` dentro de uma transação:

- A instância que lê um lote **trava** aquelas linhas até o commit.
- As outras instâncias **pulam** as linhas travadas e pegam o próximo lote.
- Resultado: **duas instâncias nunca processam o mesmo evento ao mesmo tempo**, e não é necessário lock distribuído (ShedLock, Redis etc.).

Isso depende de a consulta rodar dentro de `@Transactional` (chamada via proxy do Spring) e do MySQL 8+ com InnoDB.

## Garantias e limitações

- **Entrega at-least-once**: se a mensagem for aceita pelo SQS e a instância cair antes do commit que marca `SENT`, o evento volta a ficar disponível e é publicado de novo. **Consumidores devem ser idempotentes** (ex.: usar `idPedido` para descartar duplicatas).
- **Sem garantia de ordem**: com várias instâncias e fila SQS Standard, eventos podem chegar fora de ordem. Se a ordem importar, use fila FIFO com `messageGroupId`.
- **Latência**: o evento é publicado em até `outbox.relay.delay-ms` após o commit. Diminuir demais o intervalo aumenta a carga de polling no banco.
- **Crescimento da tabela**: eventos `SENT` não são removidos automaticamente.

## Próximos passos

- [ ] Publicação imediata após commit (`@TransactionalEventListener(AFTER_COMMIT)`) com o scheduled como fallback
- [ ] Coluna `attempts` e status `FAILED` para eventos que falham repetidamente
- [ ] Envio em lote com `sqsTemplate.sendMany(...)` (até 10 mensagens por request)
- [ ] Job de limpeza de eventos `SENT` antigos
- [ ] Consumidor `@SqsListener` idempotente
- [ ] Endpoint REST para criação de pedidos
- [ ] Testes de integração com Testcontainers (MySQL + LocalStack)
- [ ] Alternativa com CDC (Debezium) lendo o binlog do MySQL
