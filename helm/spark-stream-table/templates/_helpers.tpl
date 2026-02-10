{{/*
Expand the name of the chart.
*/}}
{{- define "spark-stream-table.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "spark-stream-table.fullname" -}}
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

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "spark-stream-table.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "spark-stream-table.labels" -}}
helm.sh/chart: {{ include "spark-stream-table.chart" . }}
{{ include "spark-stream-table.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "spark-stream-table.selectorLabels" -}}
app.kubernetes.io/name: {{ include "spark-stream-table.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: {{ .Values.spark.appName }}
component: driver
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "spark-stream-table.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "spark-stream-table.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the name of the ConfigMap
*/}}
{{- define "spark-stream-table.configMapName" -}}
{{- printf "%s-config" (include "spark-stream-table.fullname" .) }}
{{- end }}

{{/*
Create the image name
*/}}
{{- define "spark-stream-table.image" -}}
{{- printf "%s:%s" .Values.image.repository (.Values.image.tag | default .Chart.AppVersion) }}
{{- end }}

{{/*
Generate checksum annotation for ConfigMap to trigger rolling updates
*/}}
{{- define "spark-stream-table.configMapChecksum" -}}
checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
{{- end }}

