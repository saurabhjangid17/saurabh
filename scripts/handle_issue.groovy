name: Handle Jira Issue

on:
  workflow_dispatch:
    inputs:
      issue_key:
        description: 'Jira Issue Key'
        required: true
      jira_auth:
        description: 'Base64 Encoded Jira Auth'
        required: true

jobs:
  handle-issue:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repo
        uses: actions/checkout@v3

      - name: Set up Java (required for Groovy)
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Install Groovy
        run: |
          sudo apt update
          sudo apt install -y groovy

      - name: Create payload.json from Jira inputs
        run: |
          echo '{
            "issue_key": "${{ github.event.inputs.issue_key }}",
            "jira_auth": "${{ github.event.inputs.jira_auth }}"
          }' > payload.json

      - name: Show payload.json (optional for debug)
        run: cat payload.json

      - name: Run Groovy Script
        run: groovy scripts/handle_issue.groovy payload.json
