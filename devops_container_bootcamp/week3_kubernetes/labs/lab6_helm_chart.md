# Lab 6 — Custom Helm Chart

## 목표

- Lab 5의 manifest들을 Helm chart로 변환
- 환경별 values (dev/staging/prod)
- helm install / upgrade / rollback
- dependency chart (PostgreSQL)

---

## 1단계 — 새 chart 골격

```powershell
helm create my-app
cd my-app
ls
# Chart.yaml  charts/  templates/  values.yaml  .helmignore
```

기본 생성된 것들 보기:
```powershell
ls templates
# _helpers.tpl  deployment.yaml  hpa.yaml  ingress.yaml  service.yaml ...
```

기본 chart는 `nginx`를 기준으로. 우리 앱에 맞게 수정.

---

## 2단계 — Chart.yaml

```yaml
apiVersion: v2
name: my-app
description: My Spring Boot App
type: application
version: 0.1.0           # chart 버전
appVersion: "1.0.0"      # app 이미지 버전 (기본값)
keywords:
  - spring-boot
  - example
maintainers:
  - name: DK
    email: dk@example.com
```

---

## 3단계 — values.yaml

```yaml
# 기본 값
replicaCount: 2

image:
  repository: simple
  tag: "v3"
  pullPolicy: IfNotPresent

nameOverride: ""
fullnameOverride: ""

serviceAccount:
  create: true
  annotations: {}
  name: ""

podSecurityContext:
  runAsNonRoot: true
  runAsUser: 65532       # distroless nonroot

securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop: [ALL]
  readOnlyRootFilesystem: true

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: false
  className: nginx
  host: my-app.local
  tls:
    enabled: false

resources:
  requests:
    cpu: 200m
    memory: 384Mi
  limits:
    cpu: 1000m
    memory: 512Mi

autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

probes:
  enabled: true
  startup:
    failureThreshold: 30
    periodSeconds: 5
  liveness:
    periodSeconds: 10
  readiness:
    periodSeconds: 5

config:
  SPRING_PROFILES_ACTIVE: prod
  LOGGING_LEVEL_ROOT: INFO
  GREETING: "Hello from Helm"

secret:
  API_KEY: "demo-key"

env: []
# - name: EXTRA_VAR
#   value: "extra"
```

---

## 4단계 — templates/deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "my-app.fullname" . }}
  labels:
    {{- include "my-app.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "my-app.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "my-app.selectorLabels" . | nindent 8 }}
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
        checksum/secret: {{ include (print $.Template.BasePath "/secret.yaml") . | sha256sum }}
    spec:
      serviceAccountName: {{ include "my-app.serviceAccountName" . }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      containers:
        - name: {{ .Chart.Name }}
          securityContext:
            {{- toYaml .Values.securityContext | nindent 12 }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.targetPort }}
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ include "my-app.fullname" . }}-config
            - secretRef:
                name: {{ include "my-app.fullname" . }}-secret
          {{- with .Values.env }}
          env:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          {{- if .Values.probes.enabled }}
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            failureThreshold: {{ .Values.probes.startup.failureThreshold }}
            periodSeconds: {{ .Values.probes.startup.periodSeconds }}
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
          {{- end }}
```

`annotations.checksum/...`는 ConfigMap·Secret 변경 시 Pod 재시작 트리거.

---

## 5단계 — templates/configmap.yaml, secret.yaml

```yaml
# templates/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "my-app.fullname" . }}-config
  labels:
    {{- include "my-app.labels" . | nindent 4 }}
data:
  {{- range $k, $v := .Values.config }}
  {{ $k }}: {{ $v | quote }}
  {{- end }}
```

```yaml
# templates/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "my-app.fullname" . }}-secret
  labels:
    {{- include "my-app.labels" . | nindent 4 }}
type: Opaque
stringData:
  {{- range $k, $v := .Values.secret }}
  {{ $k }}: {{ $v | quote }}
  {{- end }}
```

---

## 6단계 — templates/service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "my-app.fullname" . }}
  labels:
    {{- include "my-app.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http
      protocol: TCP
      name: http
  selector:
    {{- include "my-app.selectorLabels" . | nindent 4 }}
```

---

## 7단계 — templates/ingress.yaml

```yaml
{{- if .Values.ingress.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "my-app.fullname" . }}
  labels:
    {{- include "my-app.labels" . | nindent 4 }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  rules:
    - host: {{ .Values.ingress.host }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: {{ include "my-app.fullname" . }}
                port:
                  number: {{ .Values.service.port }}
  {{- if .Values.ingress.tls.enabled }}
  tls:
    - hosts:
        - {{ .Values.ingress.host }}
      secretName: {{ include "my-app.fullname" . }}-tls
  {{- end }}
{{- end }}
```

---

## 8단계 — templates/hpa.yaml

```yaml
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "my-app.fullname" . }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "my-app.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
{{- end }}
```

---

## 9단계 — templates/_helpers.tpl

```yaml
{{- define "my-app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "my-app.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "my-app.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{ include "my-app.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "my-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "my-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "my-app.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "my-app.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
```

---

## 10단계 — 환경별 values

```yaml
# values-dev.yaml
replicaCount: 1
image:
  tag: "v3-dev"
config:
  SPRING_PROFILES_ACTIVE: dev
  LOGGING_LEVEL_ROOT: DEBUG
ingress:
  enabled: true
  host: my-app.dev.local
```

```yaml
# values-prod.yaml
replicaCount: 3
image:
  tag: "v3"
config:
  SPRING_PROFILES_ACTIVE: prod
  LOGGING_LEVEL_ROOT: WARN
ingress:
  enabled: true
  host: my-app.example.com
  tls:
    enabled: true
resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: 2000m
    memory: 1Gi
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20
```

---

## 11단계 — 배포

```powershell
# 검증
helm lint .

# rendered YAML 확인 (배포 X)
helm template my-app .
helm template my-app . -f values-dev.yaml | Select-String -Pattern "image:|replicas:|host:"

# 설치
helm install my-app . --namespace dev --create-namespace -f values-dev.yaml

# 확인
helm list -A
kubectl get all -n dev

# 업그레이드
helm upgrade my-app . --namespace dev -f values-dev.yaml --set image.tag=v4

# history
helm history my-app -n dev

# 롤백
helm rollback my-app 1 -n dev

# 삭제
helm uninstall my-app -n dev
```

---

## 12단계 — Dependency Chart (PostgreSQL)

`Chart.yaml`에 추가:

```yaml
dependencies:
  - name: postgresql
    version: "15.5.5"
    repository: https://charts.bitnami.com/bitnami
    condition: postgresql.enabled
```

```yaml
# values.yaml
postgresql:
  enabled: false        # 기본 비활성
  auth:
    username: app
    password: changeme
    database: appdb
```

```yaml
# values-dev.yaml
postgresql:
  enabled: true         # dev엔 같이 배포
```

```powershell
helm dependency update     # bitnami chart 다운로드
ls charts/                  # postgresql-15.x.x.tgz

helm install my-app . -f values-dev.yaml
# 우리 앱 + postgresql 같이 배포
```

값을 ConfigMap·Secret에 자동 연결하려면 template 수정:

```yaml
# values.yaml
config:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://{{ .Release.Name }}-postgresql:5432/appdb"
```

(Helm은 values 안에서 template 안 됨 → tpl 함수 사용 필요. 자세한 건 Helm Docs.)

---

## 산출물 체크리스트

- [ ] my-app chart 생성
- [ ] Deployment / Service / Ingress / ConfigMap / Secret 템플릿
- [ ] 환경별 values-dev/prod.yaml
- [ ] helm install / upgrade / rollback
- [ ] dependency chart (PostgreSQL)
- [ ] `helm lint` 통과
- [ ] `helm template` rendered YAML 검증

---

## 다음 단계

[Week 3 Checklist](../checklist.md)
