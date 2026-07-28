# Customer Policy 三表 Schema 设计

[English version](./schema-design.md)

## 1. 设计结论

Team 02 采用 Customer Policy v5 brief 建议的三个核心实体：

1. `policy_record`：每个 `applicationId` 一行；
2. `policy_config`：每个完整 policy version 一行，只允许 INSERT；
3. `override_log`：每次 manual override 一行，只追加、不删除。

本设计不会把 residency lists、restriction entries、rule results、sampling state 或一般
operator actions 拆成独立表。Brief 明确提醒团队不要从过于复杂的模型开始，而这三张表
已经能够覆盖 UC00-UC08 的全部锁定要求。

## 2. Source of Truth 与 Contract

Team 02 Customer Policy v5 brief 是本设计的 source of truth：

- 入站接口：`POST /api/v1/policy/execute`；
- command：`check-policy`；
- envelope：`applicationId`、`correlationId`、`command`、完整
  `application`，以及 v5 `outputs` block；
- 立即确认：HTTP `202`，body 包含 `status`、`applicationId` 和 `command`；
- 异步 callback：`POST /api/v1/callbacks`；
- outcome：`APPROVED`、`REJECTED`、`REFERRED`。

Callback 映射：

| 场景 | Outcome | Callback status | Journey effect |
|---|---|---|---|
| 自动批准 | `APPROVED` | `completed` | 继续执行下一步骤 |
| 自动拒绝 | `REJECTED` | `rejected` | 结束 journey |
| 自动转人工 | `REFERRED` | `application-manual` | 暂停并等待审核 |
| Queue decision 或 override | `APPROVED` 或 `REJECTED` | `local-manual` | 使用人工结果继续 journey |

只有 orchestrator 调用 `/execute`。Worker 可以使用完整 application，但绝不持久化
application payload。申请人详情通过本模块的 orchestrator proxy 实时获取。

目标数据库为 MySQL 8.4。Liquibase 负责 Schema，Hibernate 使用
`ddl-auto=validate`；migration 必须只追加。

## 3. 为什么三张表足够

| Use case | 所需存储 | 三表模型如何支持 |
|---|---|---|
| UC00 Process Application | `policy_record` | 返回 `202` 前插入一条 `IN_PROGRESS` row；唯一 `application_id` 保证幂等 |
| UC01 Search Cases | `policy_record` | 本地搜索 ID；姓名查询和申请人详情通过 orchestrator 实时获取；最多 10 条 |
| UC02 Review Decision | `policy_record` | 读取 `outcome`、`machine_outcome`、固定 config version 和 `rule_results` |
| UC03 View Applicant | 不需要 applicant table | 使用 `application_id` 代理 orchestrator，本地不保存 |
| UC04 Referral Queue | `policy_record` | 使用 outcome、claim 字段、人工决定字段和 optimistic locking |
| UC05 Rejection Patterns | `policy_record` | 在 `submitted_at` 时间范围内聚合 `rule_results` JSON 的 reason codes |
| UC06 Override Case | `policy_record` + `override_log` | 更新当前 outcome，并追加不可变的前后值审计记录 |
| UC07 Edit Policy Config | `policy_config` | 插入一个完整新版本，绝不更新旧版本 |
| UC08 Config History | `policy_config` | 读取全部版本；`MAX(version)` 是当前版本 |

Candidate rules UC09 和 UC10 可以在 versioned config document 中增加字段，并在
`rule_results` 中增加新 section，不需要为每条规则单独建表。

### 相对 suggested ER 的少量字段扩展

本设计保留 brief 的三个实体，只增加让 acceptance criteria 可验证所需的少量字段：

| 新增字段 | Justification |
|---|---|
| `processing_status` | UC00 要求 outcome 产生前已经存在持久化的 `IN_PROGRESS` 状态 |
| `sampling_position` | UC02 展示准确的首次决策 position，且 sampling 必须幂等 |
| `created_at`、`updated_at` | Operational ordering 和 recovery 不能依赖 applicant data |
| `lock_version` | UC04 要求两位 operator 同时领取同一 case 时确定性返回 `409` |
| `override_log.id` | 每条 append-only audit event 都需要稳定主键 |

这些只是三个建议实体上的字段，不是额外 domain table。

## 4. 实体关系图

```mermaid
erDiagram
    POLICY_CONFIG ||--o{ POLICY_RECORD : "被 case 固定引用"
    POLICY_RECORD ||--o{ OVERRIDE_LOG : "override 审计"

    POLICY_RECORD {
        varchar application_id PK
        varchar processing_status
        varchar outcome
        varchar machine_outcome
        varchar reference UK
        int policy_config_version FK
        bigint sampling_position UK
        json rule_results
        varchar claimed_by
        timestamp claimed_at
        varchar decided_by
        timestamp decided_at
        varchar decision_reason
        timestamp submitted_at
        timestamp created_at
        timestamp updated_at
        bigint lock_version
    }

    POLICY_CONFIG {
        int version PK
        json supported_residencies
        json excluded_residencies
        json restriction_list
        int sample_every
        timestamp effective_from
    }

    OVERRIDE_LOG {
        bigint id PK
        varchar application_id FK
        varchar old_outcome
        varchar new_outcome
        varchar reason
        varchar operator
        timestamp overridden_at
    }
```

## 5. 处理流程与数据归属

### 5.1 单个 Application 的运行时调用顺序

```mermaid
sequenceDiagram
    autonumber
    participant O as Module 00 Orchestrator
    participant C as Policy Controller
    participant R as policy_record
    participant W as Off-thread Policy Worker
    participant P as policy_config

    O->>C: POST /api/v1/policy/execute<br/>applicationId + 完整 application
    C->>R: INSERT applicationId, IN_PROGRESS
    Note over C,R: 只有 applicationId 可以进入 schema。<br/>Application payload 永不落库。
    R-->>C: committed
    C-->>O: 202 {status: in-progress}
    C-)W: decide(application，仅在内存中)
    W->>P: SELECT 当前 MAX(version)
    P-->>W: Residency lists + restriction list<br/>sampleEvery + version
    W->>O: 通过 Module 00 查询 registry
    O-->>W: Existing-product result 或 unavailable
    W->>R: UPDATE config version + machineOutcome<br/>outcome + ruleResults + DECIDED
    W->>O: POST /api/v1/callbacks<br/>推导出的 callback status + outcome
```

这张时序图明确了 integration boundary：Team 02 从 Module 00 接收 application，也通过
Module 00 查询 registry。它不会直接调用其他 team service，更不会读取其他 module 的
数据库。

### 5.2 Entity 的来源、归属与消费者

```mermaid
flowchart TB
    subgraph SOURCES["三个 Entity 之外的数据来源"]
        ORCH["Module 00 Orchestrator<br/>/execute: applicationId + application<br/>registry lookup response"]
        SEED["Liquibase Day-0 seed"]
        COMPLIANCE["Compliance officer<br/>Policy Configuration UI"]
        OPERATOR["Policy operator<br/>Board / Queue / Override UI"]
    end

    subgraph MODULE["Team 02 Customer Policy - 自有处理逻辑"]
        ENGINE["Rule Engine<br/>内存 application + registry result + config"]
    end

    subgraph DB["Team 02 MySQL schema - 自有持久化"]
        CONFIG["policy_config<br/>policy lists + sampleEvery<br/>insert-only versions"]
        RECORD["policy_record<br/>applicationId + decision evidence<br/>不保存 applicant profile"]
        OVERRIDE["override_log<br/>每次 override 只追加一行"]
    end

    subgraph CONSUMERS["下游消费者"]
        CALLBACK["Module 00 callback endpoint<br/>journey 继续、拒绝或暂停"]
        SCREENS["Policy UI APIs<br/>board、detail、queue、reports"]
    end

    ORCH -. "完整 application：只在内存使用" .-> ENGINE
    ORCH -. "Registry result：只在内存使用" .-> ENGINE
    SEED -- "创建 version 1" --> CONFIG
    COMPLIANCE -- "POST /config 创建下一版本" --> CONFIG
    CONFIG -- "当前版本作为 rule input" --> ENGINE

    ORCH -- "只持久化 applicationId" --> RECORD
    ENGINE -- "持久化派生 outcomes + ruleResults<br/>并固定 policy_config_version" --> RECORD
    CONFIG -- "一个 config version 对应多个 case records" --> RECORD

    OPERATOR -- "通过 service API claim / release / queue decision" --> RECORD
    OPERATOR -- "通过 service API override" --> OVERRIDE
    RECORD -- "一个 case 对应多条 override rows" --> OVERRIDE

    RECORD -- "Read models" --> SCREENS
    RECORD -- "Callback payload" --> CALLBACK
    CALLBACK --> ORCH
```

指向 table 的实线表示会持久化；虚线表示只在内存中使用的临时输入。三张数据库表都只由
Team 02 拥有：

| Entity | 谁创建或修改 | 与上下游的关系 |
|---|---|---|
| `policy_config` | Version 1 来自 Liquibase；后续版本由 compliance officer 通过 `POST /config` 创建 | 不由 orchestrator 提供；rule engine 读取；一个版本可以被多个 records 固定引用 |
| `policy_record` | `/execute` 创建；worker 完成决策；operator 修改 claim 和 decision 字段 | 只保存上游 `applicationId` 和本地派生证据；为 screens 和 callbacks 提供数据 |
| `override_log` | 只有 operator override 时追加 | `policy_record` 的 child；保存人工修改前后审计；不参与 rule evaluation |

## 6. 表定义

### 6.1 `policy_record`

每个 orchestrator application 对应一条持久化记录。该表同时承担 case、queue item、
decision trace 和 reporting source 的职责。

| 字段 | MySQL 类型 | 可空 | 数据来源 | 约束/含义 |
|---|---|---:|---|---|
| `application_id` | `VARCHAR(64)` | 否 | Orchestrator `/execute` envelope | 主键；唯一保存的 applicant-related identifier |
| `processing_status` | `VARCHAR(24)` | 否 | Customer Policy service workflow | `IN_PROGRESS` 或 `DECIDED`；初始为 `IN_PROGRESS` |
| `outcome` | `VARCHAR(16)` | 是 | Rule engine、queue reviewer 或 override request | 当前结果：`APPROVED`、`REJECTED` 或 `REFERRED` |
| `machine_outcome` | `VARCHAR(16)` | 是 | Customer Policy rule engine | Sampling 和人工介入前规则 1-3 的结果；永不覆盖 |
| `reference` | `VARCHAR(32)` | 否 | Customer Policy service 在 insert 前生成 | 唯一操作员 reference |
| `policy_config_version` | `INT` | 是 | Intake 选择的 current `policy_config` | FK → 使用的 config；返回 `202` 前固定 |
| `sampling_position` | `BIGINT` | 是 | Customer Policy intake allocator | Every-X rule 使用的唯一持久化接收序号；返回 `202` 前固定 |
| `rule_results` | `JSON` | 是 | Rule engine，由内存 application、policy config 和 live registry result 派生 | 四个嵌入式 rule sections；处理期间为空 |
| `claimed_by` | `VARCHAR(100)` | 是 | 已认证 operator，来自 claim request | 当前领取 referred case 的 operator |
| `claimed_at` | `TIMESTAMP(6)` | 是 | Claim 成功时的 service/database clock | 领取时间 |
| `decided_by` | `VARCHAR(100)` | 是 | 已认证 operator，来自 manual-decision 或 override request | 人工决定者；机器结果保持不变时为空 |
| `decided_at` | `TIMESTAMP(6)` | 是 | Human decision 或 override 成功时的 service/database clock | 人工决定或 override 时间 |
| `decision_reason` | `VARCHAR(1000)` | 是 | Operator 的 manual-decision 或 override request | 人工决定时必填的原因 |
| `submitted_at` | `TIMESTAMP(6)` | 否 | Orchestrator `application.submittedAt`（brief 中列出；见下方说明） | 用于搜索排序和日期范围报告的提交时间 |
| `created_at` | `TIMESTAMP(6)` | 否 | Intake insert 时 MySQL `CURRENT_TIMESTAMP(6)` default | 本地 row 创建时间 |
| `updated_at` | `TIMESTAMP(6)` | 否 | Customer Policy service/MySQL update timestamp | Case 最近更新时间 |
| `lock_version` | `BIGINT` | 否 | JPA optimistic locking | Claim/decision 并发使用的 optimistic-lock version |

`submitted_at` 是唯一存在 source-of-truth 冲突的字段：suggested ER 以及
queue/reporting use case 包含该字段，但 UC00 又说明 application payload
只持久化 `applicationId`。在 instructor 明确意图之前，应选择省略
`submitted_at` 并使用本地 `created_at` 排序/报表，或者先获得明确许可再持久化
`application.submittedAt`。

约束和索引：

- 主键 `application_id`；
- `reference` 唯一；
- `sampling_position` 唯一；
- FK `policy_config_version -> policy_config.version`，禁止级联删除；
- `(processing_status, created_at)` 索引；
- `(outcome, submitted_at)` 索引；
- `(claimed_by, claimed_at)` 索引；
- `policy_config_version` 索引。

由于没有 numeric auto-increment case ID，`reference` 必须在 insert 前生成。可以使用
短随机值或 ULID 派生值，例如 `pol-01J2M8R4K9`，因此无需增加 sequence table；唯一
约束负责检测极低概率的冲突。

#### `rule_results` JSON 结构

```json
{
  "existingProduct": {
    "passed": true,
    "registryChecked": true,
    "reasonCodes": []
  },
  "taxResidency": {
    "passed": true,
    "matchedList": "SUPPORTED",
    "reasonCodes": []
  },
  "restrictionList": {
    "passed": true,
    "reasonCodes": []
  },
  "sampling": {
    "sampled": false,
    "position": 20,
    "reasonCodes": []
  }
}
```

该 JSON document 的规则：

- 四个 locked sections 与 machine outcome 一起写入；
- reason codes 使用数组，因为一个 case 可能贡献多个 reason；
- 不保存 applicant name、DOB、country list、registry payload 或其他原始 application
  value；
- Sampling position 必须与关系字段 `sampling_position` 一致；
- Manual decision 和 override 不得重写 machine rule results。

MySQL 8.4 可以使用 `JSON_TABLE` 展开所有 `reasonCodes` 数组，完成 UC05。该查询必须有
真实 MySQL integration test，因为 H2 compatibility mode 不能证明 MySQL JSON 查询
行为。

### 6.2 `policy_config`

每一行代表一个完整 policy document。该表只允许 INSERT：`MAX(version)` 是当前版本，
旧版本永久保留，用于解释历史 case。

| 字段 | MySQL 类型 | 可空 | 数据来源 | 约束/含义 |
|---|---|---:|---|---|
| `version` | `INT` | 否 | Customer Policy service 分配；version 1 由 Liquibase seed | 主键；新版本为 `MAX(version) + 1` |
| `supported_residencies` | `JSON` | 否 | Version 1 来自 Liquibase seed；后续版本来自 compliance officer `POST /config` | 大写 ISO alpha-2 country code JSON array |
| `excluded_residencies` | `JSON` | 否 | Version 1 来自 Liquibase seed；后续版本来自 compliance officer `POST /config` | 大写 ISO alpha-2 country code JSON array |
| `restriction_list` | `JSON` | 否 | Version 1 来自 Liquibase seed；后续版本来自 compliance officer `POST /config` | `{fullName, dateOfBirth, reason}` object array |
| `sample_every` | `INT` | 否 | Version 1 来自 Liquibase seed；后续版本来自 compliance officer `POST /config` | 每 X 个首次决定转人工；必须至少为 1 |
| `effective_from` | `TIMESTAMP(6)` | 否 | Immutable config version insert 时的 service/database clock | 该版本成为 current 的时间 |

示例：

```json
{
  "version": 1,
  "supportedResidencies": ["GB", "IE", "PL", "DE", "FR", "ES", "NL"],
  "excludedResidencies": ["US"],
  "restrictionList": [
    {
      "fullName": "Victor Sable",
      "dateOfBirth": "1978-03-02",
      "reason": "prior fraud loss"
    },
    {
      "fullName": "Dana Kovacs",
      "dateOfBirth": "1984-11-19",
      "reason": "account abuse"
    }
  ],
  "sampleEvery": 7
}
```

Insert 前验证：

- 两个 residency 值都是 JSON array；
- 每个 country 都是大写 ISO alpha-2；
- 同一 country 不能同时出现在两个 list；
- 每条 restriction entry 必须包含非空 `fullName`、ISO date
  `dateOfBirth` 和非空 `reason`；
- 使用 normalized name + DOB 拒绝重复 restriction entry；
- `sample_every >= 1`；
- 提交的 document 必须完整；缺失 list 不能从旧版本隐式继承。

这里适合使用 JSON，因为 UC07 写入、UC08 读取的都是整个 version。Brief 不要求对单个
residency 或 restriction row 做独立 CRUD、join 或 reporting。完整 document
versioning 还可以避免 case 同时读取到新旧 list 的混合状态。

发布新版本时，在一个事务中锁定不可变的种子行 `policy_config.version = 1`，然后读取
当前最大版本并插入 `MAX(version) + 1`。已有版本永不更新或删除。

### 6.3 `override_log`

UC06 使用的 append-only audit trail。Queue decision 更新 `policy_record` 的 decision
字段；该表只记录后续 manual override。

| 字段 | MySQL 类型 | 可空 | 数据来源 | 约束/含义 |
|---|---|---:|---|---|
| `id` | `BIGINT` | 否 | MySQL auto-increment | 主键 |
| `application_id` | `VARCHAR(64)` | 否 | Override URL path 以及被引用的 `policy_record` | FK → `policy_record.application_id` |
| `old_outcome` | `VARCHAR(16)` | 否 | Override transaction 内读取的当前 `policy_record.outcome` | Override 前的 outcome |
| `new_outcome` | `VARCHAR(16)` | 否 | Operator 的 override request | `APPROVED`、`REJECTED` 或 `REFERRED` |
| `reason` | `VARCHAR(1000)` | 否 | Operator 的 override request | Operator 必填 justification |
| `operator` | `VARCHAR(255)` | 否 | 上游 override request 的 `operator` 字段 | 必填；字段名和长度与上游 contract 完全对齐 |
| `overridden_at` | `TIMESTAMP(6)` | 否 | Override 成功时的 service/database clock | Override 时间 |

约束和索引：

- FK → `policy_record`，禁止级联删除；
- `(application_id, overridden_at)` 索引；
- `(operator, overridden_at)` 索引。

Override transaction 更新 `policy_record.outcome` 和 decision metadata，并插入一条
`override_log`。它绝不修改 `machine_outcome`、`policy_config_version`、
`sampling_position` 或 `rule_results`。

## 7. 不增加第四张表的 Sampling 设计

Brief 要求每 X 个首次 policy decision 转人工，并在
`ruleResults.sampling.position` 中展示 position。

Request thread 在短 intake 事务中分配 `sampling_position`：

1. 获取 UC07 共用的 `policy_config.version = 1` allocator lock；
2. 读取最新 config version 和 `MAX(policy_record.sampling_position) + 1`；
3. 插入 case，并同时写入该 config version 和 sampling position；
4. 立即 commit；
5. 返回 `202`，然后在锁外执行 registry 和 rule calls。

`sampling_position` 唯一约束是最后的并发保护。这样可以把精确 sampling 保留在
`policy_record` 中，而不引入单独的 counter entity。必须在 MySQL 8.4 上使用并发
intake 测试该分配过程。

抽样条件：

```text
sampling_position % sample_every == 0
```

每个新 `application_id` 只执行一次 sampling；重复 `/execute` 不会再分配 position。

## 8. 状态转换、Intake、幂等与 Callback 流程

Brief 在多个层次使用了“status”一词。这些值相互关联，但不能混用：

| 层次 | 允许值 | 持久化方式与含义 |
|---|---|---|
| `202` acknowledgement | `in-progress` | 仅用于 response contract；表示 row 已 commit |
| `policy_record.processing_status` | `IN_PROGRESS`、`DECIDED` | 本地 worker lifecycle；`DECIDED` 只表示机器处理完成，不表示人工不能修改 journey |
| `policy_record.outcome` | null、`APPROVED`、`REJECTED`、`REFERRED` | 当前有效业务决定；只有 `IN_PROGRESS` 时允许为 null |
| Referral queue projection | unclaimed、claimed | 由 `outcome = REFERRED` 和 `claimed_by` 派生；不是新的持久化 status enum |
| Callback `status` | `completed`、`rejected`、`application-manual`、`local-manual` | 根据产生当前有效 outcome 的 transition，推导 orchestrator journey 指令 |

### 8.1 完整 Case State Transition

```mermaid
stateDiagram-v2
    direction LR

    state "IN_PROGRESS<br/>outcome = null" as IN_PROGRESS
    state "DECIDED / APPROVED" as APPROVED
    state "DECIDED / REJECTED" as REJECTED
    state "DECIDED / REFERRED<br/>unclaimed" as REFERRED_OPEN
    state "DECIDED / REFERRED<br/>claimed" as REFERRED_CLAIMED

    [*] --> IN_PROGRESS : 新 /execute<br/>先 insert，再返回 202 in-progress

    IN_PROGRESS --> APPROVED : 所有规则通过<br/>callback completed
    IN_PROGRESS --> REJECTED : 任一拒绝规则命中<br/>callback rejected
    IN_PROGRESS --> REFERRED_OPEN : Sampling 优先命中<br/>callback application-manual
    IN_PROGRESS --> REFERRED_OPEN : Registry 重试 3 次仍不可用<br/>callback application-manual

    REFERRED_OPEN --> REFERRED_CLAIMED : claim<br/>设置 claimed_by / claimed_at
    REFERRED_CLAIMED --> REFERRED_OPEN : release<br/>清空 claim 字段
    REFERRED_CLAIMED --> APPROVED : Queue 人工批准 + reason<br/>callback local-manual
    REFERRED_CLAIMED --> REJECTED : Queue 人工拒绝 + reason<br/>callback local-manual

    APPROVED --> REJECTED : Override + reason<br/>审计 + callback local-manual
    REJECTED --> APPROVED : Override + reason<br/>审计 + callback local-manual
    APPROVED --> REFERRED_OPEN : Override 为 REFERRED<br/>审计 + callback local-manual
    REJECTED --> REFERRED_OPEN : Override 为 REFERRED<br/>审计 + callback local-manual
```

图中的 claimed/unclaimed 是同一个持久化状态 `DECIDED + REFERRED` 的两种视图。
Claim 和 release 不修改 `processing_status` 或 `outcome`。所有人工 outcome 变更都必须
保留 `machine_outcome`、`rule_results`、`policy_config_version` 和
`sampling_position`。

因此，进入 `REFERRED` 的三种业务原因是：

1. 确定性 sampling；
2. Registry 重试三次后仍不可用；
3. Operator 将原来的 `APPROVED` 或 `REJECTED` override 为 `REFERRED`。

前两种发送 `application-manual`；第三种由 operator 发起，因此发送
`local-manual`。

### 8.2 Transition 处理

| Event | 要求的当前状态 | 原子化本地处理 | Callback / 结果 |
|---|---|---|---|
| 新 `/execute` | 不存在 row | 插入 `IN_PROGRESS`、null outcome、生成 reference 和 timestamps | 返回 `202 in-progress`；commit 后才启动 worker |
| Machine approval | `IN_PROGRESS` | 固定 config 和 sampling position；保存 rule results；machine/effective outcome 设为 `APPROVED`，status 设为 `DECIDED` | `completed` |
| Machine rejection | `IN_PROGRESS` | 保存全部 rejection reasons；machine/effective outcome 设为 `REJECTED`，status 设为 `DECIDED` | `rejected` |
| Sampled referral | `IN_PROGRESS`，机器规则已完成 | 保留 sampling 前的 `machine_outcome`；effective outcome 设为 `REFERRED`，status 设为 `DECIDED` | `application-manual` |
| Registry-unavailable referral | `IN_PROGRESS`，Registry 重试三次失败 | 保存 `POL_REGISTRY_UNAVAILABLE` 和其他 rule results；effective outcome 设为 `REFERRED`，status 设为 `DECIDED` | `application-manual` |
| Claim | Open `REFERRED`，尚未领取 | 设置 `claimed_by`、`claimed_at`，增加 `lock_version` | 不发送 callback |
| Release | `REFERRED`，由同一 operator 持有 | 清空 claim 字段，增加 `lock_version` | 不发送 callback |
| Queue decision | `REFERRED`，由决定者 claim | Outcome 设为 `APPROVED` 或 `REJECTED`；写入 operator、reason 和 decision time；保留 machine fields | `local-manual`，reason code 为 `POL_MANUAL_APPROVED` 或 `POL_MANUAL_DECLINED` |
| Override | 已有 `APPROVED` 或 `REJECTED`；target 是另一个允许值 | 更新 effective outcome 和 decision metadata；只追加一条 `override_log`；target 为 `REFERRED` 时清空 claim 字段 | `local-manual` |

### 8.3 重复请求、非法转换与失败处理

- `IN_PROGRESS` 时重复 `/execute`：返回相同 `202`；不重复 insert、不再分配
  sampling position，也不启动第二个 worker。
- `DECIDED` 后重复 `/execute`：不重跑 rules 或 registry；根据 decision source
  推导 callback status，并重放已存储 outcome。
- 同一 operator 重复 claim 是成功 no-op；其他 operator claim 返回 `409`。Case
  已经 unclaimed 时重复 release 是成功 no-op；其他 operator release 返回 `409`。
- Queue decision 或 override 与已存储的 human outcome、operator、reason 完全相同时，
  返回当前 representation，不发送第二次 callback，也不追加第二条 audit row。不同的
  stale command 返回 `409`。
- 缺少必填字段返回 `400`；未知 `application_id` 返回 `404`；optimistic-lock guard
  失败返回 `409`。这些情况都不修改数据、不发送 callback。
- Callback transport 失败不能回滚已 commit 的 decision。已存储状态可以重放；如果要求
  自动、持久化地重试 delivery attempts，则需要 outbox 或等价的第四张表。
- Worker 出现未预期异常时，row 保持 `IN_PROGRESS`。Brief 没有定义 `FAILED` 业务状态，
  recovery 必须恢复已有 row，不能自行发明 outcome。
- Applicant proxy 失败不改变 case 状态；case 仍然显示，只有实时 applicant panel
  degraded。

这里有一个实现不能自行猜测的 contract 问题：UC06 允许
`newOutcome = REFERRED`，但 callback acceptance criterion 只列出
`POL_MANUAL_APPROVED` 和 `POL_MANUAL_DECLINED`。需要 instructor 确认是否确实允许
override 到 `REFERRED`；如果允许，应使用哪个 locked reason code。不要自行新增
`POL_MANUAL_REFERRED`。

### 8.4 Intake Algorithm

返回 `202` 前：

1. 验证 `applicationId` 和 `command = check-policy`；
2. 获取 UC07 共用的 config allocator lock，读取 current config 并分配下一个
   sampling position；
3. 插入一条包含 config version 和 sampling position、且
   `processing_status = IN_PROGRESS` 的 `policy_record`；
4. 如果 `application_id` 重复，则读取现有 row；
5. commit；
6. 返回锁定的 acknowledgement body；
7. 只有 commit 后才触发处理。

Request thread 不调用 provider，也不执行 policy rule。已提交的 row 是持久化
hand-off。Recovery 扫描遗留的 `IN_PROGRESS` rows，并从 orchestrator 实时获取
application；payload 仍然不落库。

Decision worker 读取已固定的 config version 和 position，然后在一个事务中保存
rule results、outcomes 和 `processing_status = DECIDED`，再发送 callback。

重复 `/execute` 时：

- `IN_PROGRESS` case 仍返回 acknowledgement，但不会启动第二个 worker；
- 已决定 case 不会重跑规则，也不会再次查询 registry；
- 使用已存储的 outcome，并根据 outcome 和人工决定 metadata 推导 callback status
  后重放 callback。

## 9. 人工 Queue 与 Override 并发

Claim 使用 optimistic update：

```text
UPDATE policy_record
SET claimed_by = ?, claimed_at = ?, lock_version = lock_version + 1
WHERE application_id = ?
  AND outcome = 'REFERRED'
  AND claimed_by IS NULL
  AND lock_version = ?
```

更新 0 行时返回 HTTP `409`。Release 必须由同一 operator 执行。

Queue decision 要求 outcome 为 `APPROVED` 或 `REJECTED`，并要求 `operator` 和非空
reason。它更新 `outcome`、`decided_by`、`decided_at`、`decision_reason`，但不修改
machine fields，然后发送 `local-manual` callback。

Override 使用相同 optimistic-lock 规则，并额外插入一条 `override_log`。允许
`APPROVED` 改为 `REJECTED` 或 `REFERRED`，也允许 `REJECTED` 改为 `APPROVED`
或 `REFERRED`。已经 `REFERRED` 的 case 通过 claimed queue path 处理。完全相同的
重复 override 是 no-op，不产生第二条 audit row 或 callback。

## 10. 数据归属与隐私

允许存储：

- `application_id`；
- case 时间与 workflow metadata；
- 派生 outcomes 和 rule reason codes；
- 带版本的银行自有 policy configuration；
- operator claim、decision 和 override audit metadata。

禁止存储：

- 入站 request JSON；
- application 中的 applicant name、DOB、address、email、phone、nationality 或
  tax-residency array；
- customer-registry response payload；
- 本地 customer profile。

Restriction list 是银行自有 policy data，不是从 applicant 复制的数据，但仍必须只允许
授权员工访问。自由文本 decision 和 override reason 应明确提示 operator 不要输入
applicant PII。

## 11. Liquibase 计划

绝不能修改已经执行的 `001-create-demo-showcase.yaml`。

建议增加以下 append-only changesets：

1. `002-create-policy-config.yaml`
2. `003-create-policy-record.yaml`
3. `004-create-override-log.yaml`
4. `005-seed-policy-config-v1.yaml`
5. `006-drop-demo-showcase.yaml`

只有当 Java code、repository、endpoint 和 test 都不再引用 `DemoShowcase` 时，才能增加
`006`。

## 12. 验证策略

使用 H2 的 Docker-free tests：

- 重复 `/execute` 只创建一行；
- Row 在返回 `202` 前完成 commit；
- Config 和 rule validation；
- Decision precedence 和 reason-code shape；
- Claim/release conflict；
- Manual decision 和 override audit。

MySQL 8.4 Testcontainers integration tests：

- Liquibase 和 `ddl-auto=validate`；
- JSON persistence 和 `JSON_TABLE` reason aggregation；
- 并发 config version 分配；
- 并发 sampling-position 分配；
- foreign keys、unique constraints 和 timestamp precision。

该模型有意接受更复杂的 JSON 查询，以换取直接符合 brief 的更小 domain model。只有当
durable callback-attempt tracking 成为已确认要求时，才应增加 callback outbox 等第四张
operational table。
