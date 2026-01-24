# CI/CD 平台搭建实施计划（基于 ops-platform-guide.md）

## 0. 目标与边界
- 目标：在家用主机 **192.168.10.10** 上搭建生产级、可复现、可审计的 CI/CD 平台：k3s + Gitea + Tekton + Argo CD + Nexus3 + **Kuboard v3** + ingress-nginx + cert-manager + PostgreSQL + Redis，并满足“内网唯一依赖源（Nexus）”“GitOps”“Kustomize base/overlays”等约束。
  - 注意：提示词要求 Kuboard v4，但你已明确改为 v3；后续实现按 v3 执行。
- 边界：本仓库当前是 **Quarkus 应用源码**（admin-server）；平台与 GitOps 清单将放到 **两个新仓库**：`platform-infra` + `apps-deploy`。不修改业务逻辑代码。
- 环境干净：开始前对主机与集群做“可控清理/验收”，保证无残留组件/命名空间/CRD/finalizer 造成不可预测行为。
- 主机 OS：PikaOS（Debian 系），**不写死 Debian 11/12**，由 `setup.sh preflight` 读取 `/etc/os-release` 自动分支。
- DNS：由于主机 53 端口被 `verge-mihomo` 占用且不可改造，**不在 192.168.10.10 部署 dnsmasq**；改为在 `setup.sh` 中自动写入本机 `/etc/hosts`（以及按需给其他客户端提供同样的 hosts 片段），将平台域名集合解析到 ingress LB IP（默认 `192.168.10.100`）。
- Docker：**不安装**（严格符合“不依赖 Docker”约束）。

## 当前执行进度（2026-01-23）
- ✅ k3s 已安装：`v1.29.15+k3s1`（禁用 Traefik），节点 Ready
- ✅ 网络与证书：MetalLB/ingress-nginx/cert-manager（本地 CA），Ingress LB = `192.168.10.100`
- ✅ DNS 退让：不占用 53（`verge-mihomo` 占用 53 不可动），改用 `/etc/hosts`
- ✅ Argo CD：`https://argocd.local/`（Ingress + 本地 CA TLS）
- ✅ Nexus OSS：`https://nexus.local/`（Ingress + 本地 CA TLS）
- ✅ Gitea：`https://gitea.local/`（Ingress + 本地 CA TLS，数据库复用 middleware PostgreSQL）
- ✅ Kuboard v3：`https://kuboard.local/`（Ingress + 本地 CA TLS）
- ✅ 中间件层：PostgreSQL + Redis（`middleware` namespace）
- ✅ Tekton Pipelines：已安装并运行（v1.7.0），并已打通最小 CI（clone + Maven build 走 Nexus）
- ✅ 你已确认：OCI 镜像仓库也使用 Nexus（docker-hosted/docker-group）
- ✅ 已启用 Nexus Docker Registry 端口：docker-group=5000（pull）、docker-hosted=5001（push）
- ✅ Tekton 已可构建并推送 OCI 镜像到 Nexus（Quarkus + Jib，无 Docker）
- ✅ GitOps 仓库：已在 Gitea 创建并初始化
  - `platform-infra`
  - `apps-deploy`

> 说明：凭据/密码不写入仓库，请参考 `docs/ops/README.md` 的“凭据获取方式”。

## 1. 当前代码库状态分析（你现有工程）
### 1.1 应用技术栈
- Maven + Java 21；Quarkus 3.30.5；Jimmer；数据库 PostgreSQL；Redis；Flyway 迁移。
- 已包含容器构建/部署相关扩展：`quarkus-kubernetes`、`quarkus-container-image-jib`（pom.xml）。
- 已提供 Quarkus 官方 Dockerfile 模板（`src/main/docker/*`），适合 JVM/Native 两种形态。

### 1.2 配置现状与与目标规范的差距
- `src/main/resources/application.yaml` 已强调 **devservices.enabled=false**（不依赖 Docker）并用 env 变量支持覆盖 DB/Redis。
- 仍存在“平台侧要求”尚未落地的内容：
  - 仓库内没有 Kustomize `base/overlays` 结构（未发现 `kustomization.yaml`）。
  - 没有 Tekton Pipeline/Trigger、Argo CD Application、Nexus/Gitea 等平台清单。
  - pom.xml 里仍配置了阿里云 Maven 仓库（`maven.aliyun.com`），与“**Nexus 作为唯一依赖源，禁止直连公网**”要求冲突：后续应在 **CI settings.xml** 以及 **运行时/构建时镜像拉取**层面强制走 Nexus（而不是靠 pom.xml）。
  - pom.xml `distributionManagement` 指向 GitLab Package Registry（看起来是历史配置），与目标平台（Gitea/Nexus）不一致：平台方案建议改为“产物/镜像推 Nexus，部署仓库 GitOps”，不依赖 GitLab。

## 2. 发现的提示词/规范不妥点（给你可选优化）
> 下面是按“可落地、可维护、可安全”视角指出的风险点，并给出可选优化方案。

### 2.1 明文写入初始账号密码（高风险）
- 现状：ops guide 第十点写了用户名/密码。
- 风险：提示词/仓库/日志容易泄露；也不利于后续轮换与审计。
- 优化方案（可选其一）：
  1) **推荐**：只规定“账号名固定”，密码通过 `age/sops` 加密（或 1Password/Bitwarden）在本地解密后生成 Secret；仓库不出现明文。
  2) 仍用 Kustomize secretGenerator，但密码来自 **不入库的 env 文件**（.gitignore），并在 setup.sh 中交互式读取（默认 non-interactive，可通过环境变量注入）。

### 2.2 “Nexus 作为唯一依赖源”实现复杂度偏高（但可做）
- 风险：需要同时解决 Maven mirror、kaniko 拉镜像、k3s/containerd registry mirror、Helm chart 代理/托管等，且涉及证书与鉴权。
- 优化方案：
  1) **推荐**：分阶段落地：先实现 Maven mirror + 镜像 push/pull 走 Nexus（OCI），最后再把 Helm chart 也纳入 Nexus。
  2) 一次到位：需要更长时间做 bootstrap（证书/CA/registry mirror/kaniko cert）。

### 2.3 “禁止手工 kubectl apply 创建资源”与“setup.sh 从 0 到 1”天然矛盾
- 实际落地一般需要 bootstrap：第一次把 ArgoCD/Nexus/Ingress 等装进去。
- 优化方案：
  1) **推荐**：允许 setup.sh 在“bootstrap 阶段”使用 `helm upgrade --install`/`kubectl apply -f` 仅用于安装 ArgoCD 自身与基础组件；之后全部变更走 GitOps（由 Argo CD 管理的 repo）。
  2) 更严格：把 bootstrap 清单也放到 Git，然后由 setup.sh 只做“克隆仓库 + 设置 kubecontext + apply bootstrap 清单”。

### 2.4 .local 域名解析方案需明确（否则无法验收）
- 现状约束：主机 53 端口被 `verge-mihomo` 占用且不可替换/改造。
- 选择（已确认）：**仅 /etc/hosts**（主机与客户端都用 hosts 映射）。
- 验收口径：在需要访问的平台机器上 `getent hosts nexus.local` 返回 ingress LB IP（如 `192.168.10.100`）。

## 3. 版本矩阵与安装来源（必须锁定，可复现）
> 目标：把“组件版本 + 安装来源 + 关键镜像/Chart 入口”一次性钉死，后续所有脚本/Helm/Kustomize 只引用这里。

> 版本策略：**保守稳定（已确认）**，以 k3s v1.29 生态兼容优先。

### 3.1 平台组件版本矩阵（建议写入 platform-infra/versions.env）
| 组件 | 版本（锁定） | 安装来源/方式（锁定） | 说明 |
|---|---:|---|---|
| k3s | `v1.29.15+k3s1` | `https://get.k3s.io`（INSTALL_K3S_VERSION） | 关闭 Traefik；后续可启用 `--disable-default-registry-endpoint`（v1.29.1+k3s1 起支持）【k3s 私有仓库/版本门槛：https://docs.k3s.io/installation/private-registry】 |
| MetalLB | `0.15.3` | Helm repo：`https://metallb.github.io/metallb` | 用 CRD（IPAddressPool/L2Advertisement），地址池 `192.168.10.100-192.168.10.150`【repo: https://metallb.github.io/metallb/】 |
| ingress-nginx | `chart 4.11.2`（controller `v1.11.x`） | Helm repo：`https://kubernetes.github.io/ingress-nginx` | 与 k8s 1.29 兼容；后续“ingress-nginx 退役”风险需关注（长期可评估 Gateway API）【repo: https://kubernetes.github.io/ingress-nginx/】 |
| cert-manager | `v1.18.4`（对 k8s 1.29 更稳） | Helm repo：`https://charts.jetstack.io`（`--set crds.enabled=true`） | v1.19.x 主要覆盖 k8s 1.31+；我们优先选 v1.18.x【repo: https://charts.jetstack.io】 |
| Argo CD | `Helm chart 9.3.4` | Helm repo：`https://argoproj.github.io/argo-helm`（chart `argo-cd`） | chart 默认包含 ApplicationSet controller（Argo CD v2.3+ bundled）【repo: https://argoproj.github.io/argo-helm】 |
| Tekton Pipelines | `v1.7.0` | 官方 YAML：`https://infra.tekton.dev/tekton-releases/pipeline/previous/v1.7.0/release.yaml` | Tekton 官方推荐用 release YAML；后续再 GitOps 化纳管（禁手工 apply）【tekton pipelines install: https://tekton.dev/docs/pipelines/install/】 |
| Tekton Triggers | `v0.31.0` | 官方 YAML：`https://infra.tekton.dev/tekton-releases/triggers/previous/v0.31.0/release.yaml` + `https://infra.tekton.dev/tekton-releases/triggers/previous/v0.31.0/interceptors.yaml` | 用于接收 Gitea webhook 触发 PipelineRun【install: https://tekton.dev/docs/installation/triggers/】 |
| Nexus3 | `nxrm-ha chart 88.0.0`（app `3.88.0`） | Helm repo：`https://sonatype.github.io/helm3-charts/`（chart `nxrm-ha`） | Chart 元数据已验证：`nxrm-ha-88.0.0.tgz` -> appVersion `3.88.0`；要求外置 PostgreSQL（可复用 middleware/postgresql）【repo: https://sonatype.github.io/helm3-charts/】 |
| Gitea | `chart 12.4.0` | Helm repo：`https://dl.gitea.io/charts/` | 使用外置 PostgreSQL（复用 middleware/postgresql）【repo: https://dl.gitea.io/charts/】 |
| PostgreSQL | `bitnami/postgresql 18.2.0` | 先 bootstrap 直连（或改为 OCI）：`oci://registry-1.docker.io/bitnamicharts/postgresql` | 注意 Bitnami 生态策略变化；后续可迁移到社区 chart 或自建 manifest |
| Redis | `bitnami/redis 24.1.2` | 先 bootstrap 直连（或改为 OCI）：`oci://registry-1.docker.io/bitnamicharts/redis` | 同上：后续可替换来源 |
| Kuboard v3 | `kuboard-v3.yaml（上游固定入口）` | 官方 YAML：`https://addons.kuboard.cn/kuboard/kuboard-v3.yaml` | 单节点 k3s 不满足“3 节点 etcd”要求：需改为单副本/外置存储策略（计划里要做适配）【doc: https://www.kuboard.cn/install/v3/install-in-k8s.html】 |
| helm CLI（开发机/主机工具） | `v3.19.4` | 官方 release 安装 | 用于 bootstrap/脚本；后续可 pin 到本地二进制 |
| kustomize CLI | `v5.7.x`（避开 v5.8.0 回归） | 官方 release 安装 | 与 k8s 1.29 配合稳定 |
| sops | `v3.11.0` | GitHub release | Secrets 加密 |
| age | `v1.3.1` | GitHub release | sops 后端 |

### 3.2 安装来源“收敛到 Nexus-only”的路径（已选 S1）
- Bootstrap 阶段：允许直连公网拉镜像/Chart（仅为装起 Nexus/Ingress/ArgoCD 等），但**必须记录来源并可迁移到 Nexus**。
- Nexus 就绪后（强制收敛）：
  - 运行时镜像：k3s `registries.yaml` mirror 到 `nexus.local`；并在 k3s 版本满足时启用 `--disable-default-registry-endpoint` 防止回退上游。
  - Maven：Tekton 注入 `settings.xml`，`mirrorOf=*` 指向 `maven-group`（禁止 POM 直连公网仓库）。
  - Helm：把 bootstrap 用到的 Charts（metallb/ingress-nginx/cert-manager/argo-cd/nxrm-ha/gitea/postgresql/redis）逐步迁移到 Nexus（helm-proxy/hosted/group），并在脚本里把 `helm repo add` 全部替换为 `nexus.local`。
  - Tekton 官方 YAML：将 release.yaml（含镜像引用）**下载固化**到 `platform-infra/platform/tekton/upstream/`（并可选记录 sha256），由 Argo CD 纳管，避免运行时直连。

## 4. 总体交付物（仓库/目录建议，已确认“两仓库 GitOps”）
你已选择“两仓库”方案（更符合 GitOps）：

### 4.1 仓库 1：platform-infra（平台仓库）
职责：集群平台组件与 bootstrap 脚本（不含业务应用）。
建议结构：
- `versions.env`（版本锁定，唯一入口）
- `bootstrap/`（最小集：k3s/MetalLB/ingress-nginx/cert-manager/argocd）
- `platform/`（平台组件清单：nexus/gitea/tekton/kuboard/middleware）
- `sops/`
  - `age.key.pub`（提交公钥）
  - `.sops.yaml`（加密规则）
  - `secrets/`（加密后的 Secret 清单）
- `scripts/`：`setup.sh` `manage.sh` `teardown.sh`

### 4.2 仓库 2：apps-deploy（部署仓库）
职责：仅存放应用的 GitOps 部署清单，由 Argo CD 监听。
建议结构：
- `apps/admin-server/base/`
- `apps/admin-server/overlays/test/`
- `apps/admin-server/overlays/pre/`

### 4.3 统一命名与域名约定
- Nexus（OCI/Maven/Helm）：`nexus.local`
- 其他平台域名：`gitea.local` `argocd.local` `kuboard.local`
- Tekton：不部署 Dashboard（更精简）；如未来需要再增加 `tekton.local`
- 应用域名：`api-test.local` `api-pre.local`

## 4. 分阶段实施计划（Workplan）

> 约定：
> - **所有版本必须锁定**（写在 `platform-infra/versions.env`），setup.sh 只引用版本变量。
> - **所有集群资源来自 Git**：平台组件来自 `platform-infra`；应用资源来自 `apps-deploy`。
> - **bootstrap 例外**：允许 setup.sh 用 `helm upgrade --install` 安装“Argo CD/Ingress/cert-manager/MetalLB/local-path(如需)”等基础组件；后续由 Argo CD 接管平台与应用变更。

### 4.1 阶段 A：环境清理与基线验收（必须先做）
- [x] A1. 主机基线检查（preflight）：CPU/内存/磁盘、内核参数、端口占用、时间同步、iptables/nft、cgroup、systemd
  - 机制：读取 `/etc/os-release`、`uname -r`、`stat -fc %T /sys/fs/cgroup`、`systemctl is-system-running` 等
  - 验收：输出一份 `preflight` 报告（脚本 stdout），包含 OS/Kernel、k3s 版本（待装则为空）、节点 IP、磁盘剩余、必要命令存在性（curl/ssh/kubectl/helm/kustomize/sops/age）。
- [x] A2. 环境干净检查：
  - 机制：检查是否存在 `k3s` 服务、关键 namespace（argocd/tekton/nginx/platform/middleware/kuboard）、CRD（cert-manager/tekton/argoproj）与残留 local-path 数据目录
  - 验收：输出“可清理项清单”与风险提示。
- [x] A3. 若存在旧 k3s：执行 teardown（默认 clean，不 wipe）
  - 验收：k3s 相关命名空间与 CRD 清理完成；无卡住的 finalizers。
  - 回滚：若误删数据，只有在 wipe 前做了备份才可恢复。
- [x] A4. 安装/初始化 k3s（containerd），锁定版本；导出 kubeconfig
  - 版本选择：k3s **v1.29.x（已确认）**
  - 参数要点：禁用 Traefik（已确认），启用写入 `registries.yaml` 的能力
  - 验收：`kubectl get nodes -o wide` Ready；默认 StorageClass（local-path）存在。
- [ ] A5. 配置 containerd registry mirror（目标：所有镜像拉取可经 Nexus；阶段性可先留空，后续补齐）
  - 机制：写 `/etc/rancher/k3s/registries.yaml`；重启 k3s
  - 验收：阶段 D 完成后做强制验收（节点 pull 行为只访问 Nexus）。
- [x] A6. 配置 /etc/hosts（提供 *.local 解析，不占用 53 端口）
  - 机制：setup.sh 幂等写入 `/etc/hosts`（带 BEGIN/END 标记），将 `argocd.local` `nexus.local` `gitea.local` `tekton.local` `kuboard.local` 指向 ingress LB IP（默认 `192.168.10.100`）
  - 验收：本机 `getent hosts nexus.local` 命中 LB IP；浏览器可访问（证书阶段 B3 完成后 TLS 正常）。
- [ ] A7. 准备本地开发机到主机的访问：ssh、kubectl context、helm、kustomize
  - 验收：开发机可直接 `kubectl` 操作目标集群。

### 4.2 阶段 B：网络入口与证书体系（MetalLB + ingress-nginx + cert-manager）
- [x] B0. 安装 MetalLB（版本锁定）并配置地址池：`192.168.10.100-192.168.10.150`
  - 产物（platform-infra）：`bootstrap/metallb/ipaddresspool.yaml`、`bootstrap/metallb/l2advertisement.yaml`
  - 验收：创建一个 `type: LoadBalancer` Service 可获得该地址池中的 IP。
- [x] B1. 安装 ingress-nginx（版本锁定），Service 类型使用 LoadBalancer（由 MetalLB 分配 IP）
  - 产物（platform-infra）：`bootstrap/ingress-nginx/values.yaml`（或 kustomize overlay）
  - 验收：`ingress-nginx-controller` 获得固定 LB IP；从局域网可访问 80/443。
- [x] B2. 安装 cert-manager（版本锁定）
  - 产物（platform-infra）：`bootstrap/cert-manager/`（helm values + issuer/ca secret）
- [x] B3. 建立内部 CA：
  - `ClusterIssuer`: `local-ca-issuer`
  - CA 私钥：SOPS 加密存入 `platform-infra/sops/secrets/`，setup 解密后 apply
  - 验收：对 `gitea.local` 申请证书成功；证书链可被开发机信任（需导入 CA）。
- [x] B4. 域名解析落地（/etc/hosts）：
  - 机制：
    - 不在主机运行 dnsmasq；由 setup.sh 写入本机 `/etc/hosts`
    - 其他客户端手工同步同一份 hosts 片段（或后续再用 Ansible/脚本分发）
  - 验收：
    - `getent hosts gitea.local` 返回 LB IP
    - 浏览器访问任意 `xxx.local` TLS 正常（不提示不安全）。

### 4.3 阶段 C：存储与中间件（local-path + PostgreSQL + Redis）
- [ ] C1. 确认 local-path StorageClass 行为与数据目录（k3s 默认：`/var/lib/rancher/k3s/storage`）
  - 验收：PVC 绑定成功；重启节点后数据仍在。
- [ ] C2. 部署 PostgreSQL（StatefulSet + PVC + Secret）
  - 命名空间：`middleware`
  - 建议 Chart：Bitnami PostgreSQL（版本锁定）或原生 YAML（更可控但更费工）
  - 账号/密码：SOPS 加密；不在仓库出现明文
  - 验收：应用侧能连通；可执行 flyway migrations。
- [ ] C3. 部署 Redis（StatefulSet + PVC + Secret）
  - 命名空间：`middleware`
  - 建议 Chart：Bitnami Redis（版本锁定）
  - 验收：AUTH 开启；AOF/RDB 策略明确；应用侧可连通。

### 4.4 阶段 D：Nexus3（唯一依赖源：Maven + OCI + Helm）
- [ ] D0. Bootstrap 策略（已选 S1）：
  - 仅 **bootstrap 阶段** 允许集群直连公网拉取安装所需镜像/Chart
  - Nexus 就绪后：
    - 配置 k3s `registries.yaml` 镜像 mirror 指向 Nexus
    - （可选）启动 k3s `--disable-default-registry-endpoint`，禁止 containerd 回退到默认上游端点（k3s v1.29.1+k3s1 起支持）
  - 验收：节点拉镜像不再访问上游（仅访问 Nexus）

- [x] D1. 部署 Nexus3（PVC/Ingress/TLS）
  - 命名空间：`platform`
  - Ingress：`nexus.local`
  - 实际落地：采用 **Nexus OSS Helm chart（sonatype/nexus-repository-manager）**，避免 nxrm-ha（Pro）license 依赖
  - 验收：UI 可访问；PVC 正常；升级可复现。

- [ ] D2. 仓库规划（明确命名，便于幂等创建）：
  - Maven：`maven-hosted`、`maven-proxy`、`maven-group`
  - OCI(Docker)：`docker-hosted`、`docker-proxy`、`docker-group`
  - Helm：`helm-hosted`、`helm-proxy`、`helm-group`

- [ ] D3. 仓库初始化与幂等（强制工程化）：
  - 当前 Nexus OSS 版本：chart `nexus-repository-manager-64.2.0`（Nexus `3.64.0`），无需 license
  - 初始化目标不变：通过 API 幂等创建 Maven/OCI/Helm 仓库
  - 机制：使用 Nexus Script API（Groovy）创建/校验 repo（避免手工点 UI）
  - 产物（platform-infra）：`platform/nexus/scripts/`（groovy） + `scripts/nexus-init.sh`（调用 REST API 上传并执行脚本）
  - 验收：Nexus API 可查询这些仓库；重复执行不重复创建。

- [ ] D4. Maven mirror 强制：
  - 产物：Tekton 内部使用的 `settings.xml`（ConfigMap）将 `mirrorOf=*` 指向 `maven-group`
  - 验收：pipeline 构建日志中不出现 `repo1.maven.org` 等公网域名。

- [ ] D5. OCI 强制：
  - Tekton/kaniko：基础镜像从 `docker-group` 拉取；推送到 `docker-hosted`
  - k3s/containerd：`registries.yaml` 配置 `docker.io/quay.io/ghcr.io` 等镜像域名的 mirror 指向 `nexus.local`
  - TLS：kaniko 通过 `--registry-certificate` 挂载 CA；禁止长期使用 `--skip-tls-verify-registry`
  - 验收：k3s 节点拉取常见镜像时只访问 Nexus（抓包/日志/访问统计任一方式）。

- [ ] D6. Helm 强制：
  - 所有平台组件 Chart 来源统一走 Nexus `helm-group`
  - 验收：helm repo list 只包含 nexus；不直接 add 公网源。

### 4.5 阶段 E：Gitea（代码仓库）
- [ ] E1. 部署 Gitea（PVC + PostgreSQL）
  - 命名空间：`platform`
  - Ingress：`gitea.local`
  - DB：建议复用 `middleware/postgresql`（减少组件数）
  - 验收：可创建仓库/用户；SSH/HTTP clone 正常。
- [ ] E2. 仓库与分支策略落地：
  - Repo1：`admin-quarkus-jimmer`（源码）
  - Repo2：`apps-deploy`（GitOps 清单，base/overlays/test|pre）
  - 分支：`main`/`develop` + feature/*；CI 触发按分支或 tag 区分环境
- [ ] E3. 凭据与 Webhook/Token：
  - Tekton 访问 git：用 Secret（basic auth/token/ssh key 二选一）
  - Webhook：只发到 Tekton Trigger，避免外部暴露。

### 4.6 阶段 F：Tekton（CI）
- [ ] F1. 安装 Tekton Pipelines + Triggers（Dashboard 可选）
  - 命名空间：`tekton-pipelines`（官方默认）
  - 验收：CRD 就绪；能跑一个 hello-world TaskRun。
- [ ] F2. Pipeline 设计（两条，严格区分环境/构建形态）：
  - `quarkus-jvm-pipeline` -> test
  - `quarkus-native-pipeline` -> pre
  - 产物：`platform-infra/platform/tekton/pipelines/*.yaml`
- [ ] F3. 统一工作空间与缓存策略（避免重复下载）：
  - Maven 本地仓库缓存：PVC or emptyDir（按资源权衡）
  - settings.xml 注入：ConfigMap（mirrorOf=* -> Nexus maven-group）
- [ ] F4. 镜像构建与推送：
  - 使用 kaniko
  - 目标：`nexus.local` 的 `docker-hosted`
  - 记录元数据：git sha、image digest、PipelineRun name（写回 apps-deploy commit message 或 annotation）
- [ ] F5. GitOps 更新策略：
  - Tekton 只改 `apps-deploy` 仓库（kustomize image tag 或 kustomization.yaml images 块），**禁止**直接 kubectl
  - 验收：推送后 Argo CD 自动/半自动同步生效。
- [ ] F6. Trigger 规则：
  - `develop` -> test（JVM）
  - `main` 或 tag `v*` -> pre（Native）

### 4.7 阶段 G：Argo CD（CD / GitOps）
- [ ] G1. 安装 Argo CD（Ingress/TLS）
  - 命名空间：`argocd`
  - Ingress：`argocd.local`
  - 验收：登录、创建 Application 正常。
- [ ] G2. GitOps 仓库结构（apps-deploy）：
  - `apps/admin-server/base`
  - `apps/admin-server/overlays/test`
  - `apps/admin-server/overlays/pre`
  - 约定：test 与 pre 使用不同 namespace：`quarkus-test`、`quarkus-pre`
- [ ] G3. 应用配置注入（强制）：
  - ConfigMap：挂载为 `application.yaml`（推荐）或 envFrom（二选一全局统一）
  - Secret：DB/Redis 密码、JWT key 等
  - 变更生效机制：对 ConfigMap/Secret 内容计算 checksum，写入 Deployment annotation 触发滚动更新
  - 回滚：Argo CD 回滚到上一 Git revision。
- [ ] G4. Argo CD App 定义（已选 ApplicationSet）：
  - 使用 `ApplicationSet` 管理 apps-deploy 中的多环境（test/pre）
  - 使用 `ApplicationSet` 或单独 `Application` 管理 platform-infra 中的平台组件（建议也用 ApplicationSet，按目录生成）
  - 同步策略：test 自动同步；pre 可设置手动 approve（更安全）
  - 验收：新增一个 overlay 目录可自动生成/纳管对应 Application。

### 4.8 阶段 H：Kuboard v3（集群管理）
- [ ] H0. 安装方式取舍（提示词要求“不装 Docker”，Kuboard 官方更推荐 docker run）：
  - 方案 A（推荐，符合本平台约束）：**安装到 Kubernetes 集群内**（Ingress/TLS，走 Nexus 镜像代理）
  - 方案 B（不符合当前约束）：docker run 独立容器（需要安装 Docker/Podman）
- [ ] H1. 安装 Kuboard v3 到 k3s（Ingress/TLS）
  - 命名空间：`kuboard`
  - Ingress：`kuboard.local`
  - 权限：最小化 RBAC（只读/运维分角色，后续增强）
  - 验收：可导入集群 kubeconfig 或使用 kuboard-agent；能查看节点/工作负载。
  - 备注：Kuboard v3 与 k8s v1.29 兼容（Kuboard 文档列为已验证）。

### 4.9 阶段 I：脚本体系（setup/manage/teardown，幂等可重入）
> 所有脚本位于 **platform-infra** 仓库。

- [ ] I0. 脚本接口约定（统一 UX）：
  - `./scripts/setup.sh preflight|bootstrap|install|verify`
  - `./scripts/manage.sh status|wait-ready|logs|diag|start|stop|restart --target <label>`
  - `./scripts/teardown.sh clean|wipe|reset --force [--dry-run]`

- [ ] I1. `setup.sh preflight`（环境干净 + 依赖检查）
  - 输出：OS/Kernel/cgroup、k3s 状态、端口占用、是否已存在平台命名空间/CRD、dnsmasq 状态
  - 失败即退出：避免半拉子状态

- [ ] I2. `setup.sh bootstrap`（最小集可启动）
  - 安装/初始化 k3s（如需要）
  - 安装 MetalLB（配置地址池）
  - 安装 ingress-nginx（LB IP）
  - 安装 cert-manager（ClusterIssuer + CA Secret）
  - 安装 Argo CD（Ingress/TLS）
  - 验收：`argocd.local` 可访问

- [ ] I3. `setup.sh install`（通过 Argo CD 接管平台与应用）
  - 配置 Argo CD repo credentials（指向 platform-infra 与 apps-deploy）
  - 创建 Argo CD Applications：
    - 平台应用（platform components）：nexus/gitea/tekton/kuboard/middleware（Tekton 不含 Dashboard）
    - 业务应用（admin-server）：test/pre 两个 Application
  - 验收：Argo CD “Healthy/Synced”

- [ ] I4. `setup.sh verify`（验收用例集合）
  - DNS：`dig nexus.local` 命中 LB IP
  - TLS：访问 nexus/gitea/argocd/kuboard 证书有效
  - CI：一次 Tekton pipeline 端到端
  - CD：apps-deploy tag 更新触发 Argo 滚动更新

- [ ] I5. `manage.sh`（日常运维入口）
  - status：摘要输出关键命名空间/pods/svc/ingress/cert
  - wait-ready：等待就绪并输出阻塞原因
  - diag：describe/events/logs 摘要（只输出可行动信息）

- [ ] I6. `teardown.sh`（清理干净，可控保留）
  - clean：卸载 Argo Applications/命名空间/CRD，处理 finalizers
  - wipe：可选删除 PVC/PV/local-path 目录（高危，必须 --force）
  - reset：可选卸载 k3s 并清理残留（iptables/cni/containerd 镜像缓存按需）

### 4.10 阶段 J：验收与文档（可教学/可审计）
- [ ] J1. 端到端验收（必须可重复执行）：
  - develop 提交 -> Tekton JVM pipeline -> push 镜像 -> 更新 apps-deploy(test) -> Argo 同步 -> `api-test.local` 可访问
  - main/tag -> Tekton Native pipeline -> push 镜像 -> 更新 apps-deploy(pre) -> Argo 同步 -> `api-pre.local` 可访问
- [ ] J2. 依赖源验收（必须）：
  - Maven 只走 Nexus
  - 镜像拉取/推送只走 Nexus
  - Helm chart 来源只走 Nexus
- [ ] J3. 文档：
  - 架构总览（组件图 + 数据流）
  - CI/CD 流程与分支/环境映射
  - 访问域名清单：`nexus.local` `gitea.local` `argocd.local` `kuboard.local` `api-test.local` `api-pre.local`
  - （可选）Tekton Dashboard：如启用则增加 `tekton.local`
  - 脚本使用说明与幂等保证
  - 证书体系与 CA 信任导入步骤
  - Secret 管理（SOPS/age）与轮换流程

## 5. 关键设计选择（已确认）
- k3s 版本：**v1.29.x**
- Ingress：禁用 k3s Traefik，使用 **ingress-nginx**
- 入口暴露方式：MetalLB（LoadBalancer）
- MetalLB 地址池：`192.168.10.100-192.168.10.150`
- 存储方案：local-path（k3s 默认本地存储类，单机可用）
- Secret 管理：SOPS + age（仓库可审计、无明文；**age 私钥仅保存在 192.168.10.10**：`~/.config/sops/age/keys.txt`）
- 仓库模型：两仓库 GitOps（platform-infra + apps-deploy）
- Argo CD：ApplicationSet 管理 apps/platform
- DNS：在 192.168.10.10 跑 dnsmasq 解析 `*.local`
- Nexus 域名：`nexus.local`
- Tekton UI：不部署 Dashboard（更精简）
- Docker：不安装

---
备注：当前处于 PLAN 模式，本计划只覆盖“怎么做”，不开始实际搭建；下面将把每个阶段细化到具体目录结构、版本锁定、清单边界、脚本接口、验收标准与回滚路径。
