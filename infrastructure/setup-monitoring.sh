#!/bin/bash

# GCP Infrastructure Monitoring & Cost Tracking Setup
# This script sets up monitoring, logging, and cost management for the chat app

set -e

echo "🚀 Setting up GCP Infrastructure Monitoring & Cost Tracking..."

# Configuration
PROJECT_ID=${1:-"your-chat-app-project"}
SERVICE_ACCOUNT_NAME="chat-app-monitoring"
BUDGET_AMOUNT=${2:-10}
BUDGET_NAME="chat-app-budget"

echo "📋 Project ID: $PROJECT_ID"
echo "💰 Budget Amount: \$$BUDGET_AMOUNT"

# Create monitoring service account
echo "🔐 Creating monitoring service account..."
gcloud iam service-accounts create $SERVICE_ACCOUNT_NAME \
  --description="Service account for chat app monitoring and alerting" \
  --display-name="Chat App Monitoring"

SERVICE_ACCOUNT_EMAIL="$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com"

# Grant necessary permissions
echo "🔑 Granting monitoring permissions..."
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SERVICE_ACCOUNT_EMAIL" \
  --role="roles/monitoring.editor"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SERVICE_ACCOUNT_EMAIL" \
  --role="roles/logging.viewer"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SERVICE_ACCOUNT_EMAIL" \
  --role="roles/billing.viewer"

# Create budget and alerts
echo "💸 Setting up budget monitoring..."
gcloud billing budgets create $BUDGET_NAME \
  --billing-account=$(gcloud billing projects describe $PROJECT_ID --format="value(billingAccountName)") \
  --display-name="Chat App Monthly Budget" \
  --budget-amount=$BUDGET_AMOUNT \
  --budget-type=AMOUNT

# Create notification channel (email)
echo "📧 Creating notification channel..."
NOTIFICATION_CHANNEL=$(gcloud beta monitoring channels create \
  --display-name="Chat App Alerts" \
  --type=email \
  --channel-labels=email_address=your-email@example.com \
  --format="value(name)")

echo "📊 Notification channel created: $NOTIFICATION_CHANNEL"

# Create Cloud Run monitoring dashboard
echo "📈 Creating Cloud Run monitoring dashboard..."
cat > cloud-run-dashboard.json << EOF
{
  "displayName": "Chat App - Cloud Run Monitoring",
  "gridLayout": {
    "widgets": [
      {
        "title": "Request Count",
        "xyChart": {
          "dataSets": [{
            "plotType": "LINE",
            "targetAxis": "Y1",
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"run.googleapis.com/request_count\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"chat-backend\"",
                "aggregation": {
                  "perSeriesAligner": "ALIGN_RATE",
                  "crossSeriesReducer": "REDUCE_SUM",
                  "groupByFields": []
                }
              }
            }
          }],
          "timeshiftDuration": "0s",
          "yAxis": {
            "label": "requests/sec",
            "scale": "LINEAR"
          }
        }
      },
      {
        "title": "Request Latency",
        "xyChart": {
          "dataSets": [{
            "plotType": "LINE",
            "targetAxis": "Y1",
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"run.googleapis.com/request_latencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"chat-backend\"",
                "aggregation": {
                  "perSeriesAligner": "ALIGN_PERCENTILE_95",
                  "crossSeriesReducer": "REDUCE_MEAN",
                  "groupByFields": []
                }
              }
            }
          }],
          "timeshiftDuration": "0s",
          "yAxis": {
            "label": "latency (ms)",
            "scale": "LINEAR"
          }
        }
      },
      {
        "title": "Active Connections",
        "xyChart": {
          "dataSets": [{
            "plotType": "LINE",
            "targetAxis": "Y1",
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"run.googleapis.com/container/cpu/utilization\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"chat-backend\"",
                "aggregation": {
                  "perSeriesAligner": "ALIGN_MEAN",
                  "crossSeriesReducer": "REDUCE_MEAN",
                  "groupByFields": []
                }
              }
            }
          }],
          "timeshiftDuration": "0s",
          "yAxis": {
            "label": "utilization",
            "scale": "LINEAR"
          }
        }
      }
    ]
  }
}
EOF

gcloud monitoring dashboards create --config-from-file=cloud-run-dashboard.json

# Create alerting policies
echo "🚨 Creating alerting policies..."

# High latency alert
gcloud alpha monitoring policies create \
  --display-name="Chat App - High Request Latency" \
  --condition-display-name="95th percentile latency > 1s" \
  --filter="metric.type=\"run.googleapis.com/request_latencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"chat-backend\"" \
  --duration="300s" \
  --comparison="COMPARISON_GT" \
  --threshold-value="1" \
  --threshold-aggregation="ALIGN_PERCENTILE_95" \
  --notification-channels="$NOTIFICATION_CHANNEL" \
  --aggregation-cross-series-reducer="REDUCE_MEAN"

# High error rate alert
gcloud alpha monitoring policies create \
  --display-name="Chat App - High Error Rate" \
  --condition-display-name="Error rate > 5%" \
  --filter="metric.type=\"run.googleapis.com/request_count\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"chat-backend\" metric.label.\"response_code_class\"=\"4xx\" OR metric.label.\"response_code_class\"=\"5xx\"" \
  --duration="300s" \
  --comparison="COMPARISON_GT" \
  --threshold-value="0.05" \
  --threshold-aggregation="ALIGN_RATE" \
  --notification-channels="$NOTIFICATION_CHANNEL"

# CPU utilization alert
gcloud alpha monitoring policies create \
  --display-name="Chat App - High CPU Utilization" \
  --condition-display-name="CPU utilization > 80%" \
  --filter="metric.type=\"run.googleapis.com/container/cpu/utilization\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"chat-backend\"" \
  --duration="300s" \
  --comparison="COMPARISON_GT" \
  --threshold-value="0.8" \
  --threshold-aggregation="ALIGN_MEAN" \
  --notification-channels="$NOTIFICATION_CHANNEL"

# Create uptime check
echo "🔍 Creating uptime check..."
gcloud monitoring uptime-checks create https \
  --display-name="Chat App Backend Uptime" \
  --resource-type=uptime-url \
  --resource-labels=host=chat-backend-url \
  --checked-resource-ssl-cert \
  --path="/health" \
  --check-interval=60s \
  --timeout=10s \
  --notification-channels="$NOTIFICATION_CHANNEL"

# Enable Cloud Logging for detailed logs
echo "📝 Enabling detailed logging..."
gcloud logging sinks create chat-app-logs \
  storage.googleapis.com/projects/$PROJECT_ID/buckets/chat-app-logs \
  --log-filter="resource.type=cloud_run_revision AND resource.labels.service_name=chat-backend"

echo "✅ Infrastructure monitoring setup complete!"
echo ""
echo "📋 Summary:"
echo "  - Service Account: $SERVICE_ACCOUNT_EMAIL"
echo "  - Budget: $BUDGET_NAME ($$BUDGET_AMOUNT/month)"
echo "  - Dashboard: Chat App - Cloud Run Monitoring"
echo "  - Alert Policies: 3 configured (Latency, Errors, CPU)"
echo "  - Uptime Check: Configured for /health endpoint"
echo "  - Log Sink: chat-app-logs"
echo ""
echo "🔧 Next Steps:"
echo "  1. Update notification email address in the script"
echo "  2. Update uptime check with actual Cloud Run URL"
echo "  3. Review and adjust alert thresholds as needed"
echo "  4. Set up log-based metrics for custom monitoring"
echo ""
echo "💡 Monitoring URLs:"
echo "  - Dashboard: https://console.cloud.google.com/monitoring/dashboards"
echo "  - Logs: https://console.cloud.google.com/logs"
echo "  - Budgets: https://console.cloud.google.com/billing"
