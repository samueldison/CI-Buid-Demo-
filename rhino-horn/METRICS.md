*Install metrics-server*

```bash
# Add the metrics-server helm repository
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/

# Update the helm repositories
helm repo update

# Install metrics-server
helm upgrade --install metrics-server metrics-server/metrics-server -n kube-system

# Edit metrics-server deployment
kubectl edit deployment metrics-server -n kube-system
# Add these 2 lines:
--kubelet-preferred-address-types=InternalIP
--kubelet-insecure-tls=true

# Get metrics-server pods
kubectl get pods -l app.kubernetes.io/name=metrics-server -n kube-system

# Get metrics-server deployment
kubectl get deployment metrics-server -n kube-system

# Describe metrics-server deployment
kubectl describe deployment metrics-server -n kube-system

# Check cpu and memory usage
kubectl top nodes

# Check cpu and memory usage of pods
kubectl top pods -A

# Check cpu and memory usage of pods in a specific namespace
kubectl top pods -n <namespace>

# Check cpu and memory usage of a specific pod
kubectl top pod <pod-name> -n <namespace>

# Check cpu and memory usage of all pods
kubectl top pod --all-namespaces

# Check cpu and memory usage of pods in a specific namespace
kubectl top pod -n <namespace>

# Check cpu and memory usage of a specific pod
kubectl top pod <pod-name> -n <namespace>

# Uninstall metrics-server
helm uninstall metrics-server -n kube-system
```