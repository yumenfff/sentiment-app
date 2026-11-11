# Sentiment App: ИИ-приложение в Kubernetes

Java-приложение с mock-моделью анализа тональности текста, развёрнутое в Minikube: 3 реплики, Service (LoadBalancer), Ingress, HPA по CPU, мониторинг Prometheus + Grafana.

## Эндпоинты

| Путь                            | Назначение                                                                         |
| ------------------------------- | ---------------------------------------------------------------------------------- |
| `GET /api/sentiment?text=hello` | Анализ тональности: `{"sentiment":"positive"}` (`positive`, `negative`, `neutral`) |
| `GET /api/info`                 | Имя пода (для демонстрации балансировки)                                           |
| `GET /api/load?ms=300`          | Нагрузка на CPU (для демонстрации HPA)                                             |
| `GET /actuator/prometheus`      | Метрики для Prometheus                                                             |
| `GET /actuator/health`          | Проверка состояния                                                                 |

Модель сделана на словаре положительных и отрицательных слов. Каждый запрос учитывается счётчиком `sentiment_requests_total`.

## Стек

Java 21, Spring Boot 3.5.0 (Web, Actuator), Micrometer Prometheus, Maven; Docker (multi-stage, `jlink`, Alpine 3.22, образ около 95 МБ); Minikube, Kubernetes, Helm, kube-prometheus-stack.

Проверено на MacBook с Apple M1 (arm64), 8 ГБ ОЗУ, Docker Desktop.

## Структура

```
.
├── Dockerfile
├── pom.xml
├── src/main/java/com/example/sentiment/   # код приложения
├── src/main/resources/application.properties
├── k8s/                                   # манифесты Kubernetes
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── hpa.yaml
│   └── servicemonitor.yaml
└── monitoring/values.yaml                 # настройки Helm для Prometheus и Grafana
```

## 1. Сборка и локальный запуск

```bash
docker build -t sentiment-app:1.0 .
docker run -d --name sentiment-test -p 8080:8080 sentiment-app:1.0
curl "http://localhost:8080/api/sentiment?text=hello"
docker rm -f sentiment-test
```

Размер образа смотрите по слоям: `docker history sentiment-app:1.0`.

## 2. Кластер Minikube

```bash
brew install minikube kubectl helm
minikube start --driver=docker --cpus=3 --memory=3500mb --nodes=2
minikube addons enable ingress
minikube addons enable metrics-server
kubectl get nodes
```

## 3. Развёртывание

```bash
minikube image load sentiment-app:1.0
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/ingress.yaml -f k8s/hpa.yaml
kubectl get deployment,pods,svc,ingress,hpa -o wide
```

Для внешнего доступа запустите в отдельном окне терминала (потребуется пароль, порты 80 и 443):

```bash
minikube tunnel
```

Проверка:

```bash
curl "http://127.0.0.1:8080/api/sentiment?text=hello"    # через Service
curl "http://127.0.0.1/api/sentiment?text=hello"         # через Ingress
for i in 1 2 3 4 5 6; do curl -s "http://127.0.0.1/api/info"; echo; done   # разные поды
```

## 4. Автомасштабирование (HPA)

HPA: 3-6 реплик, цель 50% CPU от requests (100m). Нагрузка изнутри кластера:

```bash
kubectl run load-generator --image=busybox:1.36 --restart=Never -- \
  /bin/sh -c 'while true; do wget -q -O- "http://sentiment-app:8080/api/load?ms=300" > /dev/null; done'
kubectl run load-generator-2 --image=busybox:1.36 --restart=Never -- \
  /bin/sh -c 'while true; do wget -q -O- "http://sentiment-app:8080/api/load?ms=300" > /dev/null; done'
kubectl get hpa -w
```

Через 1-3 минуты реплик станет 6 (событие `SuccessfulRescale` в `kubectl describe hpa sentiment-app`). Остановить нагрузку:

```bash
kubectl delete pod load-generator load-generator-2 --now
```

## 5. Мониторинг: Prometheus и Grafana

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
kubectl create namespace monitoring
helm install prometheus prometheus-community/kube-prometheus-stack \
  -n monitoring -f monitoring/values.yaml --version 92.22.0 \
  --set grafana.resources.requests.memory=200Mi \
  --set grafana.resources.limits.memory=700Mi \
  --set grafana.livenessProbe.initialDelaySeconds=240 \
  --set grafana.livenessProbe.failureThreshold=30 \
  --set grafana.livenessProbe.timeoutSeconds=30 \
  --set grafana.readinessProbe.initialDelaySeconds=60 \
  --set grafana.readinessProbe.timeoutSeconds=30
kubectl get pods -n monitoring -w
kubectl apply -f k8s/servicemonitor.yaml
```

Интерфейсы (каждая команда в отдельном окне):

```bash
kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090   # http://localhost:9090/targets
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80      # http://localhost:3000 (admin / admin)
```

В Prometheus на странице Targets цель `serviceMonitor/default/sentiment-app/0` должна быть `3 / 3 up`.

Дашборд в Grafana: Dashboards → New → Import → ID `4701` (JVM Micrometer). Если панели пустые с ошибкой `Datasource ${DS_PROMETHEUS} was not found`, экспортируйте дашборд в JSON, замените `${DS_PROMETHEUS}` на `prometheus` и импортируйте файл заново.

## Остановка и удаление

```bash
minikube stop      # остановить кластер
minikube delete    # удалить кластер полностью
```
