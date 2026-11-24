# 防汛平台状态迁移与规则升级服务

某市防汛平台在汛期中途把固定阈值风险规则升级为多因子规则（同时考虑**上游水位趋势、泵站启停滞后、传感器可信度**）。
旧作业保存的是**网格窗口状态**，新规则需要**泵站—河段—道路三层中间状态**。本服务提供状态迁移与规则升级的全流程治理能力：

- **流定义登记表**：输入主题、事件时间字段、水位基准、状态模式、规则包版本、分区→道路/泵站/河段映射；
- **检查点清单**：每个分区的消费位点、窗口水印、状态快照、输出批次，并提供检查点比对（位点/水印滞后与就绪度）；
- **状态语义证明器**：比较旧新字段、聚合粒度与依赖关系，给出旧状态确实可转换的守恒条件；
- **迁移计划器**：按分区选择 `ADAPT`（经证明的状态适配）/ `REPLAY`（限定时间窗回放）/ `MANUAL`（人工裁定），支持混合路线；
- **双轨校验器**：旧新规则在同一输入截点并行计算并量化风险差异；
- **切换闸门**：仅当关键分区满足证据要求且覆盖率达标时发布新输出，否则维持旧输出并标记待验证；
- **回退控制器**：保留旧规则只读快照，撤回指定范围内的新输出；
- **事件证据账**：每次风险结论记录输入、规则与状态版本，区分 `OLD_OUTPUT` / `NEW_CANDIDATE` / `NEW_OUTPUT` / `RETRACTED`。

本服务同时提供**泵站排水贡献核算**能力（设备日志的"运行"不等于有效排水：闸门关闭、回流或流量计反向时，
按运行分钟计贡献会高估能力）：

- **设备沿革**：把控制器、泵组和流量计绑定到有效区间，多台泵组可共用同一出水道；
- **水量平衡器**：按上下游水位、进出流量和闸门状态逐段计算净排量及不确定区间；
- **对账器**：标出无法闭合的窗口（及子区间），并把原因反馈到设备质量记录；
- **流域汇总**：只累计通过物理方向校验的净量；
- **运行效率报表**：只统计闭合窗口，校准误差跨阈的待核窗口不进入报表。

## 技术栈

Java 25 · Spring Boot 4.0 · Spring Data JPA · PostgreSQL 18 · springdoc-openapi（Swagger UI）· Maven

## 快速开始

### 方式一：Docker Compose 一键启动（应用 + PostgreSQL）

```bash
docker compose up --build
```

启动后访问 http://localhost:8080/swagger-ui.html 。

### 方式二：本地开发

前置：JDK 25、Maven 3.9+、PostgreSQL（库/用户/密码均为 `ccdev`，见 `src/main/resources/application.yml`，可用 `DB_URL/DB_USER/DB_PASSWORD` 覆盖）。

```bash
# 运行测试（使用 ccdev_test 库，首次需创建：CREATE DATABASE ccdev_test OWNER ccdev;）
mvn test

# 打包并启动（首次启动自动建表并写入演示数据）
mvn package -DskipTests
java -jar target/state-migration-service-1.0.0.jar
```

### 测试账号（HTTP Basic）

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `operator` | `operator123` | OPERATOR |
| `admin` | `admin123` | ADMIN, OPERATOR |

Swagger UI（`/swagger-ui.html`）与 OpenAPI 文档（`/v3/api-docs`）无需登录；`/api/**` 需上述账号。

## 接口一览

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 流定义 | `POST /api/streams`、`GET /api/streams[/{id}]` | 登记/查询流定义 |
| 规则包 | `POST /api/streams/{id}/rule-packages`、`GET .../rule-packages` | 登记/二次修订；多因子修订使相关比较与证明过期并重新绑定目标版本 |
| 流事件 | `POST /api/streams/{id}/events`、`GET .../events` | 事件写入（位点按分区自增）与查询 |
| 检查点 | `POST /api/streams/{id}/checkpoints`、`GET .../checkpoints` | 登记/查询检查点清单 |
| 检查点比对 | `GET /api/streams/{id}/checkpoint-compare` | 位点/水印滞后、快照与迁移就绪度比对 |
| 证明器 | `POST /api/streams/{id}/proofs`、`GET /api/proofs/{id}` | 生成状态语义证明（守恒条件） |
| 迁移编排 | `POST /api/streams/{id}/migration-plans`、`POST .../execute-all`、`POST /api/migration-plans/{id}/execute`、`POST /api/migration-plans/{id}/adjudicate` | 计划生成/执行/人工裁定 |
| 双轨校验 | `POST /api/streams/{id}/dual-track-runs`、`GET /api/dual-track-runs/{id}/diffs` | 同截点并行计算与差异量化 |
| 切换闸门 | `POST /api/streams/{id}/gate/evaluate`、`POST .../gate/release`、`GET .../gate` | 评估/发布/状态 |
| 回退控制 | `POST /api/streams/{id}/rollbacks`、`GET .../rollbacks` | 撤回输出范围、旧快照只读 |
| 证据账 | `GET /api/streams/{id}/evidence?partitionNo=&kind=` | 按分区/类别查询证据 |
| 泵站登记 | `POST /api/pump-stations`、`GET /api/pump-stations[/{code}]` | 登记/查询泵站（调蓄池面积、量测误差、校准误差安全阈值） |
| 设备沿革 | `POST /api/pump-stations/{code}/lineage`、`GET .../lineage` | 控制器/泵组/流量计绑定到有效区间 |
| 校准曲线 | `POST /api/pump-stations/{code}/calibration-curves`、`GET .../calibration-curves` | 泵组 Q-H 校准曲线、校准误差与有效区间 |
| 站端遥测 | `POST /api/pump-stations/{code}/telemetry`、`GET .../telemetry?kind=&from=&to=` | 水位/进出流量/闸门状态/泵组启停写入与查询 |
| 排水核算 | `POST /api/pump-stations/{code}/drainage-runs`、`GET .../drainage-runs`、`GET /api/drainage-runs/{id}` | 运行核算（两路线+对账）与结果查询；同窗口重跑幂等替换 |
| 设备质量记录 | `GET /api/pump-stations/{code}/quality-records`、`GET /api/drainage-runs/{id}/quality-records` | 对账器反馈的反向流量计/闸门关闭运行/缺校准段等记录 |
| 运行效率报表 | `GET /api/pump-stations/{code}/efficiency?from=&to=` | 仅闭合窗口计入；待核/未闭合窗口列入排除清单 |
| 流域汇总 | `GET /api/basins/{basinCode}/drainage-summary?from=&to=` | 只累计通过物理方向校验的净量 |

## 迁移路线选择语义

- **状态适配（ADAPT）**：旧聚合拆解/提升为三层状态，延迟小。仅当守恒条件满足时选用：
  - `PUMP_ON_OFF_CONSERVATION`：由启停事件推导的窗口内泵运行时长须与快照累计量一致（容差 1 分钟）；
  - `WINDOW_GRANULARITY_MATCH`：旧窗口须为约两小时，与新规则累计粒度一致。
  快照缺失启停事件时**适配器必须拒绝该快照，禁止以零值填充缺失启停区间**；执行时若发现累计量不守恒，计划器自动**转为限定时间窗回放**。
- **限定窗口回放（REPLAY）**：从冻结位点对受影响分区回放原始事件重算三层状态，语义最直接；回放窗口受事件保留范围约束，保留不足时降级为**人工裁定（MANUAL）**。
- 计划器依据状态可逆性（证明结论）、事件保留范围与汛情时效（关键分区优先适配）为各分区选择或混合两条路线。

## 关键业务规则

1. 新规则把某道路由低风险改为高风险、而旧作业尚未完成该分区检查点时，闸门**维持旧输出**并把该分区标记为**待验证**（`pendingVerification`），补齐检查点后方可放行。
2. 双轨期间规则二次修订（如可信度规则 v2.0.0→v2.1.0）：相关双轨比较与适配证明**过期**，引用旧证明的计划置为 `STALE`，目标版本**重新绑定**到新规则包，须重新证明与双轨后才能发布。
3. 回放分区越过切换水印时，证据账区分：已发布新输出（越水印部分**撤回**为 `RETRACTED`）、本次回放结果仅作**新候选**（`NEW_CANDIDATE`）、旧输出（`OLD_OUTPUT`）保持不变。
4. 回退保留旧规则**只读快照**，撤回 `[fromOffset, toOffset]` 范围内的新输出，闸门回到 `HOLD`，流恢复旧规则服务。

## 泵站排水贡献核算模型

**物理模型**：控制体为站前调蓄池，守恒 `dS/dt = Q_in − Q_out`（`S = 站前水位 × 水面面积`）；
出水闸门位于排水通道上，**闸门关闭时出水道断开**，泵组运行只在站内循环，有效净排量为 0
（运行分钟不计贡献）。水位按点值线性插值，流量/闸门/泵状态按最近一次读数步进。

**两条路线统一估计同一泵站同一时间窗的站级总有效净排量**（同一目标，不是两个口径）：

- **路线 A（泵组校准曲线积分）**：逐段按泵组 Q-H 校准曲线 `Q(扬程)` 积分（扬程 = 下游水位 − 上游水位，
  仅运行且闸门开启的分段计贡献）。局部归因清楚；**缺校准段**（曲线失效或扬程超出覆盖范围）
  **无法给单点量**，此时路线 A 站级总量为空、仅记录已覆盖部分参考量。
- **路线 B（泵站控制体守恒）**：逐段 `Q_in·dt − ΔS` 反推站级总净排量，覆盖更完整，
  但无法将共用出水道的量精确分给单泵。出水道流量计（符号改正后）作为交叉校验。

**对账与反馈**：

- 路线 A 校准完整且 `|A−B|` 在容差内（`max(50 m³, 10%)`）、出水道交叉校验一致 → 窗口**闭合**，
  统一估计取两路线不确定区间交集；否则窗口**无法闭合**（`UNCLOSED`），不给出统一单点量，
  未闭合区间（如缺校准段的具体时段）随结果返回。
- **只有泵组校准曲线完整时才附带单泵分解**（`perPumpJson`），分解口径不是独立目标。
- 无法闭合的原因（缺校准段、两路线偏差、方向校验失败）与运行中发现的问题
  （流量计反向、闸门关闭运行、校准误差跨阈、沿革绑定失效）都反馈到**设备质量记录**。
- **物理方向校验**：净流向与排水方向相反（允许不确定区间内微小反向）的窗口不进入流域汇总。
- **校准误差跨过安全阈值**（泵站级 `safetyErrorPct`）的窗口进入**待核**（`PENDING_REVIEW`），
  不进入运行效率报表；流域汇总只累计通过物理方向校验且已有统一估计的净量。

## 演示数据（首次启动自动写入）

流 `FY-MAIN`（4 个分区，p0/p1/p2 为关键分区）：

| 分区 | 道路 | 初始状态 | 预期路线 |
| --- | --- | --- | --- |
| 0 | R-001 | 检查点完成，启停守恒满足 | ADAPT |
| 1 | R-002 | 快照缺失启停事件 | REPLAY（禁止零值填充） |
| 2 | R-003 | 旧作业检查点未完成；新规则 LOW→HIGH | REPLAY + 闸门待验证 |
| 3 | R-004 | 检查点完成，空启停守恒满足 | ADAPT |

流域 `BASIN-01` 三座泵站（核算窗口起点为启动前 8 小时，启动时自动完成核算）：

| 泵站 | 场景 | 核算结果 |
| --- | --- | --- |
| PS-01 城区东站 | 两台共用出水道 OUT-1 的泵（PG-1/PG-2）、反转流量计 FM-OUT、闸门关闭运行 1 小时（T0+2h~T0+3h 泵仍在转）、泵站前后水位变化；PG-2 校准曲线只覆盖到 T0+3h | 路线 B = 19440 m³；路线 A 缺校准段无法给单点量（已覆盖 16200 m³）；**UNCLOSED**，未闭合区间 [T0+3h, T0+4h]；闸门关闭段两路线净量均为 0；质量记录含 METER_REVERSED / GATE_CLOSED_RUNNING / CALIBRATION_GAP |
| PS-02 临江站 | 单泵，校准误差 20% 跨过安全阈值 15% | 路线 A = 路线 B = 2160 m³，两路线闭合但 **PENDING_REVIEW**，不进入运行效率报表 |
| PS-03 高新区站 | 单泵，非常量校准曲线（扬程插值），误差 4% | 路线 B = 3240 m³、路线 A ≈ 3268.8 m³，**CLOSED**，统一估计取区间交集，附单泵分解 |

## 验证方式（本仓库交付前实际执行过的验证）

```bash
# 1) 自动化测试：32 个用例全部通过（证明器/计划器/适配器回退/双轨/闸门/规则修订/回退/证据账/接口鉴权
#    + 排水核算两路线站级总量/未闭合区间/闸门关闭零贡献/反转流量计/待核/流域汇总方向校验/效率报表排除）
mvn test

# 2) 端到端冒烟（应用已启动、演示数据为初始状态）：12 步完整升级流程，输出 SMOKE_OK
scripts/smoke.sh

# 3) 泵站排水核算冒烟：10 步（设备沿革→两路线总量→未闭合区间→闸门关闭零贡献→质量记录
#    →幂等重跑→待核→单泵分解→效率报表排除→流域汇总方向校验），输出 SMOKE_OK
scripts/smoke-drainage.sh

# 4) 重复冒烟前先重置演示数据（清空业务表并重启应用重新播种）
scripts/reset-db.sh && scripts/smoke.sh && scripts/smoke-drainage.sh
```

冒烟流程：证明 → 迁移编排（p0/p3 适配、p1/p2 回放）→ 执行 → 双轨（p2 低→高升级）→ 闸门待验证 →
补齐 p2 检查点 → 规则二次修订 v2.1.0（证明/比较过期、计划 STALE）→ 重新绑定 → 发布新输出 →
p1 回放越过切换水印（已发布输出撤回、结果仅作候选）→ 回退（快照只读、范围撤回）→ 证据账分类统计。

排水核算冒烟流程：泵站/设备沿革/校准曲线查验 → PS-01 两路线站级总量与缺校准段未闭合 →
闸门关闭分段两路线净量为 0（运行 4h 中关闭 1h 不计贡献）→ 设备质量记录（反转流量计/闸门关闭运行/缺校准段）→
同窗口幂等重跑 → PS-02 校准误差跨阈进入待核 → PS-03 闭合并附单泵分解 →
运行效率报表排除待核与未闭合窗口 → 流域汇总只累计通过物理方向校验的净量。

> 说明：本开发容器无 Docker 守护进程，`docker compose up --build` 未在本环境实际执行；
> Dockerfile/Compose 使用标准基础镜像（`maven:3.9-eclipse-temurin-25`、`eclipse-temurin:25-jre`、`postgres:18`），
> 应用本体已通过上述 `mvn test` 与 `scripts/smoke.sh`、`scripts/smoke-drainage.sh` 在相同 jar 上验证。

## 目录结构

```
src/main/java/com/flood/statemigrate/
├── domain/      # 实体与枚举（流定义/规则包/检查点/证明/计划/双轨/闸门/回退/证据/事件
│                #   + 泵站/设备沿革/校准曲线/站端遥测/核算运行/设备质量记录）
├── repo/        # Spring Data JPA 仓库
├── service/     # 证明器、计划器（适配+回放）、双轨、闸门、回退、证据账、聚合与规则引擎
│                #   + 水量平衡器、对账器、排水核算编排、报表服务、泵站登记
├── web/         # REST 控制器与请求 DTO
├── config/      # 安全、OpenAPI、演示数据种子（迁移演示 + 泵站排水演示）
└── error/       # 统一异常处理
scripts/         # smoke.sh 迁移冒烟、smoke-drainage.sh 排水核算冒烟、reset-db.sh 演示数据重置
docker/          # Maven 镜像仓库配置（构建加速）
Dockerfile · docker-compose.yml
```
