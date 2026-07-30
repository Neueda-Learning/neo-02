# Customer Policy — Live Demo Script

这份台本演示一条完整的 Customer Policy 操作旅程：

1. Sidecar 发送一个正常申请；
2. 在 Applications 和 Search cases 中查看该申请；
3. 发布新的 Policy Config；
4. 证明原本可通过的申请会被新策略拒绝；
5. 发送一个被抽样转人工的申请；
6. 在 Referral Queue 中领取并人工批准。

预计演示时间：6–8 分钟。

## 0. Demo 前准备

### 必须从干净的 seed policy v1 开始

本台本依赖以下确定性状态：

- 当前 Policy Config 是 v1；
- `policy_record` 中只有五条 seed records；
- 最大 `sampling_position` 是 `705`；
- 演示过程中不要发送台本之外的申请。

重置并启动：

```bash
docker compose down -v
docker compose up -d --build
```

确认服务：

```bash
docker compose ps
```

可选的数据库确认：

```bash
docker compose exec -T mysql mysql -uappuser -papppass neo_02 \
  -e "SELECT MAX(version) AS current_config FROM policy_config;
      SELECT COUNT(*) AS records, MAX(sampling_position) AS max_position FROM policy_record;"
```

预期：

```text
current_config = 1
records = 5
max_position = 705
```

打开两个浏览器标签页：

- Sidecar: `http://localhost:9000`
- Customer Policy UI: `http://localhost:5173`

本台本使用三个本地 Sidecar presets：

| 顺序 | Sidecar scenario | Applicant | 关键数据 | 预期结果 |
|---|---|---|---|---|
| 1 | `pol-demo-approve-01` | Alice Morgan | Tax residency `GB` | `APPROVED` under v1 |
| 2 | `pol-demo-approve-09` | Sophie Bennett | Tax residency `GB` | `REJECTED` under v2 |
| 3 | `pol-demo-approve-02` | Lukas Weber | Tax residency `DE` | `REFERRED` by sampling |

> Sidecar 发送时会给 application ID 添加序号，例如
> `POL-DEMO-APPROVE-01-1`。演示时以 Sidecar Ack 和前端显示的实际 ID 为准。

---

## 1. Send a happy application

### 操作

1. 打开 Sidecar。
2. 选择 `pol-demo-approve-01`。
3. 确认 Module URL 指向 Docker backend：`http://backend:8080`。
4. 点击 **Send**。
5. 等待页面显示 `202 Ack` 和 callback。
6. 记下 Sidecar 显示的实际 application ID。

预期 callback：

```text
ACCEPTED
POL_ALL_CHECKS_PASSED
```

预期 policy record：

```text
sampling position: 706
policy config: v1
machine outcome: APPROVED
effective outcome: APPROVED
```

### 建议讲解词

> “Applications arrive from the orchestrator through the fixed onboarding contract.  
> I am sending a normal customer application from our local Sidecar, which plays the
> orchestrator during development.”

> “The service acknowledges the request immediately with HTTP 202, processes the policy
> asynchronously, stores the decision, and sends the result back to the orchestrator.”

---

## 2. Show what we received in Applications

### 操作

1. 切换到 Customer Policy UI。
2. 打开左侧 **Applications**。
3. 等待最多两秒，让列表自动刷新。
4. 找到刚才的 application ID。
5. 指出状态、reference 和 submitted time。
6. 点击该行进入 **Decision detail**。

在详情页展示：

- Effective outcome: `APPROVED`
- Machine outcome: `APPROVED`
- Policy config: `v1`
- Existing product: passed
- Tax residency: supported
- Restriction list: passed
- Sampling: not sampled
- Applicant information: Alice Morgan

### 建议讲解词

> “This is the durable application board. The request is no longer just a log entry — it has
> a stored policy reference, status, decision evidence, and the exact config version used.”

> “The decision detail explains the result rule by rule. Applicant information is fetched live
> from the orchestrator when we open the case, while the policy decision and its evidence are
> stored by this service.”

---

## 3. Find the same application in Search cases

### 操作

1. 打开左侧 **Search cases**。
2. 先粘贴实际 application ID，点击搜索。
3. 展示搜索结果。
4. 可选：清空后输入 `Alice Morgan` 再次搜索。
5. 点击结果，证明它打开的是同一个 Decision detail。

### 建议讲解词

> “An operator can find the same case either by application ID or by applicant name. Search
> returns the durable policy record, and selecting it opens the same evidence-backed decision.”

---

## 4. Edit Policy Config

### 演示目标

发布 v2，做两个修改：

1. 从 supported residencies 中移除 `GB`；
2. 将 **Sample every (X)** 从 `7` 改为 `2`。

这样可以稳定演示：

- 下一条 GB application 位于 position `707`，不会被 `sampleEvery=2` 抽中，因此会
  因新策略被 `REJECTED`；
- 再下一条 DE application 位于 position `708`，会被抽样为 `REFERRED`。

### 操作

1. 打开左侧 **Policy Config**。
2. 指出当前版本是 `v1 / CURRENT`。
3. 点击右上角 **Edit Policy Config**。
4. 将 **Supported residencies** 从：

   ```text
   GB, IE, PL, DE, FR, ES, NL
   ```

   改为：

   ```text
   IE, PL, DE, FR, ES, NL
   ```

5. 保持 **Excluded residencies** 为：

   ```text
   US
   ```

6. 保持 restriction list 不变。
7. 将 **Sample every (X)** 改为：

   ```text
   2
   ```

8. 点击弹窗底部 **Edit Policy Config** 发布。
9. 确认页面显示 `Policy version 2 is now current`。

### 建议讲解词

> “Policy is managed as versioned data rather than hard-coded application logic. I will remove
> GB from the supported tax-residency list and increase the manual-review sampling frequency.”

> “Publishing creates a new immutable version. Existing decisions remain pinned to v1; only
> applications accepted after this point use v2.”

---

## 5. Send an application that was previously OK, now rejected

### 操作

1. 返回 Sidecar。
2. 选择 `pol-demo-approve-09`（Sophie Bennett，tax residency `GB`）。
3. 点击 **Send**。
4. 等待 callback。

预期 callback：

```text
REJECTED
POL_TAX_RESIDENCY_UNSUPPORTED
```

预期 policy record：

```text
sampling position: 707
policy config: v2
machine outcome: REJECTED
effective outcome: REJECTED
```

5. 返回 **Applications**。
6. 找到新 application，展示红色 `REJECTED` 状态。
7. 打开 Decision detail。
8. 指出 Policy config 是 `v2`，Tax residency check 为 failed。

### 建议讲解词

> “This application is structurally valid and would have passed the original seed policy.
> However, it was accepted after v2 became current, and its GB tax residency is no longer on
> the supported list.”

> “The rejection is explainable: the case is pinned to v2 and records the exact reason code,
> `POL_TAX_RESIDENCY_UNSUPPORTED`.”

---

## 6. Send an application that is referred

### 操作

1. 返回 Sidecar。
2. 选择 `pol-demo-approve-02`（Lukas Weber，tax residency `DE`）。
3. 点击 **Send**。
4. 等待 callback。

预期 callback：

```text
REFERRED
POL_SAMPLED_FOR_REVIEW
```

预期 policy record：

```text
sampling position: 708
policy config: v2
machine outcome: APPROVED
effective outcome: REFERRED
```

### 建议讲解词

> “This customer passes the business rules because DE remains supported. But v2 samples every
> second first-time decision, and position 708 is selected for manual review.”

> “Notice the distinction between machine outcome and effective outcome: the machine recommends
> approval, while the journey is parked as referred until a human reviews it.”

---

## 7. Review and approve from Referral Queue

### 操作

1. 打开左侧 **Referral Queue**。
2. 如未立即出现，等待最多五秒或点击 **Refresh**。
3. 在 **Operator ID** 输入：

   ```text
   demo.operator
   ```

4. 找到刚才的 Lukas Weber application。
5. 指出：
   - Referral cause: sampling
   - Machine outcome: `APPROVED`
   - Claim state: `Unclaimed`
6. 点击 **Claim**。
7. 点击该行进入 Decision detail。
8. 在 **Decision reason** 输入：

   ```text
   Customer reviewed; no blocking policy concern found.
   ```

9. 点击 **Approve**。

预期结果：

- Effective outcome 从 `REFERRED` 变为 `APPROVED`；
- Machine outcome 仍保持 `APPROVED`；
- 页面显示 Human decision、operator、时间和 reason；
- 该记录从 Referral Queue 消失；
- Sidecar 收到最终 `ACCEPTED` callback。

### 建议讲解词

> “The referral queue is the human-control point. An operator identifies themselves, claims the
> case so another reviewer cannot work it at the same time, and records an auditable reason.”

> “Approving the referral changes the effective outcome, but it does not erase the machine
> recommendation or the original rule evidence. We retain both automation and human accountability.”

---

## 8. Closing statement

### 建议讲解词

> “In one journey we have shown asynchronous orchestration, durable and explainable decisions,
> application search, versioned policy configuration, deterministic rejection under a new policy,
> risk-based referral, and a fully audited human decision.”

> “The key point is that policy changes are traceable: old cases keep their original config,
> new cases use the latest version, and manual intervention never destroys the machine evidence.”

## Quick recovery guide

| Demo symptom | Likely cause | Recovery |
|---|---|---|
| First application is not `APPROVED` | Database was not reset to seed v1 | Run `docker compose down -v`, then `docker compose up -d --build` |
| First case is not position `706` | Extra applications were sent | Reset and follow only this script |
| Sophie becomes `REFERRED` instead of `REJECTED` | Sampling position drifted | Reset; ensure Sophie is exactly the second new application |
| Lukas is not `REFERRED` | v2 was not published with `sampleEvery=2`, or position drifted | Check Policy Config and reset if needed |
| Applicant panel is unavailable | Sidecar was restarted without retaining its DB, or the application did not originate there | Re-send from Sidecar after reset |
| Referral does not appear immediately | Queue polls every five seconds | Click **Refresh** |
| Approve is disabled | Case is claimed by another operator ID | Use the claiming ID or release the claim first |

## Demo assets

- [15 Sidecar policy sample POSTs](sidecar-policy-sample-posts.md)
- [Local Sidecar overlay](../spec/fixtures/uc02-sidecar/README.md)
