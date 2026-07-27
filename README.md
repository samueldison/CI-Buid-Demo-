# Port forward:
kubectl port-forward -n mlops svc/rhino-horn 8084:8084

# Test Health:
curl http://localhost:8084/actuator/health

# Check startupProbe:
kubectl get deployment rhino-horn -n mlops -o yaml | grep -A 20

# Check the actual deployed container ports and probe config
kubectl get deployment rhino-horn -n mlops -o yaml | grep -A 40
