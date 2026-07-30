# Sidecar policy sample POSTs

这 15 个完整 application envelope 按数据库的 seed policy v1 设计，可逐个粘贴到
Sidecar 的发送页面（`http://localhost:9000`）。每个 `applicationId` 都不同，因此会生成
15 条独立的 policy record。

Seed policy v1：

- Supported tax residencies: `GB`, `IE`, `PL`, `DE`, `FR`, `ES`, `NL`
- Excluded tax residencies: `US`
- Restriction list: `Victor Sable / 1978-03-02`、`Dana Kovacs / 1984-11-19`
- Sampling: every 7th unique record is `REFERRED`

前 10 条的机器决策应为 `APPROVED`，后 5 条的机器决策应为 `REJECTED`。由于 sampling
position 取决于数据库中已有数据，任意第 7 倍数位置会把最终结果改为 `REFERRED`；这是
预期行为，不代表样例错误。

## Expected APPROVED — 01

```json
{
  "applicationId": "POL-DEMO-APPROVE-01",
  "correlationId": "pol-demo-approve-0001",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-01",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:01:00Z",
    "applicant": {
      "fullName": "Alice Morgan",
      "dateOfBirth": "1991-02-14",
      "email": "alice.morgan@example.com",
      "mobile": "+447700910001",
      "nationality": "GB",
      "countryOfResidence": "GB",
      "taxResidencies": ["GB"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "10 King Street", "line2": null, "city": "London", "postcode": "SW1A 1AA", "country": "GB"},
      "monthsAtAddress": 24,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "GBD1000001", "issuingCountry": "GB", "expiryDate": "2031-02-14"},
    "employment": {"status": "PERMANENT", "employerName": "Northstar Retail", "monthsInEmployment": 36},
    "finances": {"annualIncome": 42000, "monthlyHousingCost": 1100, "existingCreditCommitments": 150},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 3000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected APPROVED — 02

```json
{
  "applicationId": "POL-DEMO-APPROVE-02",
  "correlationId": "pol-demo-approve-0002",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-02",
    "channel": "WEB",
    "submittedAt": "2026-07-30T09:02:00Z",
    "applicant": {
      "fullName": "Lukas Weber",
      "dateOfBirth": "1987-06-23",
      "email": "lukas.weber@example.com",
      "mobile": "+447700910002",
      "nationality": "DE",
      "countryOfResidence": "GB",
      "taxResidencies": ["DE"],
      "residentialStatus": "MORTGAGE",
      "currentAddress": {"line1": "22 Bridge Road", "line2": null, "city": "Manchester", "postcode": "M1 1AE", "country": "GB"},
      "monthsAtAddress": 48,
      "dependants": 1
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "DED1000002", "issuingCountry": "DE", "expiryDate": "2032-06-23"},
    "employment": {"status": "PERMANENT", "employerName": "Pennine Systems", "monthsInEmployment": 60},
    "finances": {"annualIncome": 51000, "monthlyHousingCost": 1250, "existingCreditCommitments": 200},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 4000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected APPROVED — 03

```json
{
  "applicationId": "POL-DEMO-APPROVE-03",
  "correlationId": "pol-demo-approve-0003",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-03",
    "channel": "BRANCH",
    "submittedAt": "2026-07-30T09:03:00Z",
    "applicant": {
      "fullName": "Ewa Kowalska",
      "dateOfBirth": "1994-10-05",
      "email": "ewa.kowalska@example.com",
      "mobile": "+447700910003",
      "nationality": "PL",
      "countryOfResidence": "GB",
      "taxResidencies": ["PL"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "8 Market Lane", "line2": "Flat 3", "city": "Leeds", "postcode": "LS1 2AB", "country": "GB"},
      "monthsAtAddress": 18,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "PLD1000003", "issuingCountry": "PL", "expiryDate": "2030-10-05"},
    "employment": {"status": "PERMANENT", "employerName": "Yorkshire Health", "monthsInEmployment": 29},
    "finances": {"annualIncome": 36000, "monthlyHousingCost": 900, "existingCreditCommitments": 100},
    "product": {"productCode": "CREDIT_CARD_STANDARD", "requestedCreditLimit": 2200},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": true}
  }
}
```

## Expected APPROVED — 04

```json
{
  "applicationId": "POL-DEMO-APPROVE-04",
  "correlationId": "pol-demo-approve-0004",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-04",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:04:00Z",
    "applicant": {
      "fullName": "Aoife Murphy",
      "dateOfBirth": "1989-12-19",
      "email": "aoife.murphy@example.com",
      "mobile": "+447700910004",
      "nationality": "IE",
      "countryOfResidence": "GB",
      "taxResidencies": ["IE"],
      "residentialStatus": "OWNER",
      "currentAddress": {"line1": "16 Harbour View", "line2": null, "city": "Bristol", "postcode": "BS1 4QA", "country": "GB"},
      "monthsAtAddress": 72,
      "dependants": 2
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "IED1000004", "issuingCountry": "IE", "expiryDate": "2033-12-19"},
    "employment": {"status": "PERMANENT", "employerName": "Harbour Analytics", "monthsInEmployment": 81},
    "finances": {"annualIncome": 58000, "monthlyHousingCost": 1200, "existingCreditCommitments": 250},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 5000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected APPROVED — 05

```json
{
  "applicationId": "POL-DEMO-APPROVE-05",
  "correlationId": "pol-demo-approve-0005",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-05",
    "channel": "WEB",
    "submittedAt": "2026-07-30T09:05:00Z",
    "applicant": {
      "fullName": "Carlos Ruiz",
      "dateOfBirth": "1990-08-11",
      "email": "carlos.ruiz@example.com",
      "mobile": "+447700910005",
      "nationality": "ES",
      "countryOfResidence": "GB",
      "taxResidencies": ["ES"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "41 Queen Street", "line2": null, "city": "Cardiff", "postcode": "CF10 2AG", "country": "GB"},
      "monthsAtAddress": 31,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "ESD1000005", "issuingCountry": "ES", "expiryDate": "2031-08-11"},
    "employment": {"status": "PERMANENT", "employerName": "Severn Digital", "monthsInEmployment": 42},
    "finances": {"annualIncome": 39000, "monthlyHousingCost": 950, "existingCreditCommitments": 120},
    "product": {"productCode": "CREDIT_CARD_STANDARD", "requestedCreditLimit": 2500},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": false, "marketingConsent": false}
  }
}
```

## Expected APPROVED — 06

```json
{
  "applicationId": "POL-DEMO-APPROVE-06",
  "correlationId": "pol-demo-approve-0006",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-06",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:06:00Z",
    "applicant": {
      "fullName": "Claire Martin",
      "dateOfBirth": "1995-03-27",
      "email": "claire.martin@example.com",
      "mobile": "+447700910006",
      "nationality": "FR",
      "countryOfResidence": "GB",
      "taxResidencies": ["FR"],
      "residentialStatus": "LIVING_WITH_FAMILY",
      "currentAddress": {"line1": "7 Station Road", "line2": null, "city": "Birmingham", "postcode": "B1 1BB", "country": "GB"},
      "monthsAtAddress": 54,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "FRD1000006", "issuingCountry": "FR", "expiryDate": "2032-03-27"},
    "employment": {"status": "CONTRACT", "employerName": "Midlands Design", "monthsInEmployment": 17},
    "finances": {"annualIncome": 33000, "monthlyHousingCost": 600, "existingCreditCommitments": 80},
    "product": {"productCode": "CREDIT_CARD_STANDARD", "requestedCreditLimit": 1800},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": true}
  }
}
```

## Expected APPROVED — 07

```json
{
  "applicationId": "POL-DEMO-APPROVE-07",
  "correlationId": "pol-demo-approve-0007",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-07",
    "channel": "PHONE",
    "submittedAt": "2026-07-30T09:07:00Z",
    "applicant": {
      "fullName": "Emma de Vries",
      "dateOfBirth": "1986-01-30",
      "email": "emma.devries@example.com",
      "mobile": "+447700910007",
      "nationality": "NL",
      "countryOfResidence": "GB",
      "taxResidencies": ["NL"],
      "residentialStatus": "MORTGAGE",
      "currentAddress": {"line1": "29 Castle Street", "line2": null, "city": "Edinburgh", "postcode": "EH1 2NB", "country": "GB"},
      "monthsAtAddress": 67,
      "dependants": 1
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "NLD1000007", "issuingCountry": "NL", "expiryDate": "2030-01-30"},
    "employment": {"status": "PERMANENT", "employerName": "Caledonia Finance", "monthsInEmployment": 74},
    "finances": {"annualIncome": 61000, "monthlyHousingCost": 1400, "existingCreditCommitments": 220},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 6000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected APPROVED — 08

```json
{
  "applicationId": "POL-DEMO-APPROVE-08",
  "correlationId": "pol-demo-approve-0008",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-08",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:08:00Z",
    "applicant": {
      "fullName": "Noah Williams",
      "dateOfBirth": "1998-07-16",
      "email": "noah.williams@example.com",
      "mobile": "+447700910008",
      "nationality": "GB",
      "countryOfResidence": "GB",
      "taxResidencies": ["GB", "IE"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "5 Lime Street", "line2": "Flat 8", "city": "Liverpool", "postcode": "L1 1JQ", "country": "GB"},
      "monthsAtAddress": 15,
      "dependants": 0
    },
    "identityDocument": {"type": "DRIVING_LICENCE", "documentId": "WILLI807168NW9", "issuingCountry": "GB", "expiryDate": "2031-07-16"},
    "employment": {"status": "PERMANENT", "employerName": "Mersey Logistics", "monthsInEmployment": 26},
    "finances": {"annualIncome": 35000, "monthlyHousingCost": 850, "existingCreditCommitments": 90},
    "product": {"productCode": "CREDIT_CARD_STANDARD", "requestedCreditLimit": 2000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected APPROVED — 09

```json
{
  "applicationId": "POL-DEMO-APPROVE-09",
  "correlationId": "pol-demo-approve-0009",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-09",
    "channel": "WEB",
    "submittedAt": "2026-07-30T09:09:00Z",
    "applicant": {
      "fullName": "Sophie Bennett",
      "dateOfBirth": "1992-09-08",
      "email": "sophie.bennett@example.com",
      "mobile": "+447700910009",
      "nationality": "GB",
      "countryOfResidence": "GB",
      "taxResidencies": ["GB"],
      "residentialStatus": "OWNER",
      "currentAddress": {"line1": "12 Park Row", "line2": null, "city": "Nottingham", "postcode": "NG1 6GR", "country": "GB"},
      "monthsAtAddress": 39,
      "dependants": 1
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "GBD1000009", "issuingCountry": "GB", "expiryDate": "2034-09-08"},
    "employment": {"status": "SELF_EMPLOYED", "employerName": "Bennett Studio", "monthsInEmployment": 46},
    "finances": {"annualIncome": 47000, "monthlyHousingCost": 1050, "existingCreditCommitments": 130},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 3500},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": false, "marketingConsent": true}
  }
}
```

## Expected APPROVED — 10

```json
{
  "applicationId": "POL-DEMO-APPROVE-10",
  "correlationId": "pol-demo-approve-0010",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-APPROVE-10",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:10:00Z",
    "applicant": {
      "fullName": "Mateo Garcia",
      "dateOfBirth": "1988-04-22",
      "email": "mateo.garcia@example.com",
      "mobile": "+447700910010",
      "nationality": "ES",
      "countryOfResidence": "GB",
      "taxResidencies": ["ES", "GB"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "33 Broad Street", "line2": null, "city": "Oxford", "postcode": "OX1 3BD", "country": "GB"},
      "monthsAtAddress": 28,
      "dependants": 2
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "ESD1000010", "issuingCountry": "ES", "expiryDate": "2032-04-22"},
    "employment": {"status": "PERMANENT", "employerName": "Thames Research", "monthsInEmployment": 53},
    "finances": {"annualIncome": 54000, "monthlyHousingCost": 1300, "existingCreditCommitments": 180},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 4500},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected REJECTED — 01 · excluded US residency

Expected reason: `POL_TAX_RESIDENCY_EXCLUDED`.

```json
{
  "applicationId": "POL-DEMO-REJECT-01",
  "correlationId": "pol-demo-reject-0001",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-REJECT-01",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:11:00Z",
    "applicant": {
      "fullName": "Olivia Carter",
      "dateOfBirth": "1985-05-17",
      "email": "olivia.carter@example.com",
      "mobile": "+447700910011",
      "nationality": "US",
      "countryOfResidence": "GB",
      "taxResidencies": ["GB", "US"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "18 Baker Street", "line2": null, "city": "London", "postcode": "W1U 3BW", "country": "GB"},
      "monthsAtAddress": 20,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "USD1000011", "issuingCountry": "US", "expiryDate": "2031-05-17"},
    "employment": {"status": "PERMANENT", "employerName": "Atlantic Consulting", "monthsInEmployment": 48},
    "finances": {"annualIncome": 60000, "monthlyHousingCost": 1500, "existingCreditCommitments": 200},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 4000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected REJECTED — 02 · unsupported BR residency

Expected reason: `POL_TAX_RESIDENCY_UNSUPPORTED`.

```json
{
  "applicationId": "POL-DEMO-REJECT-02",
  "correlationId": "pol-demo-reject-0002",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-REJECT-02",
    "channel": "WEB",
    "submittedAt": "2026-07-30T09:12:00Z",
    "applicant": {
      "fullName": "Rafael Almeida",
      "dateOfBirth": "1993-11-04",
      "email": "rafael.almeida@example.com",
      "mobile": "+447700910012",
      "nationality": "BR",
      "countryOfResidence": "GB",
      "taxResidencies": ["BR"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "27 Temple Way", "line2": null, "city": "Bristol", "postcode": "BS2 0EL", "country": "GB"},
      "monthsAtAddress": 22,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "BRD1000012", "issuingCountry": "BR", "expiryDate": "2030-11-04"},
    "employment": {"status": "PERMANENT", "employerName": "Harbour Media", "monthsInEmployment": 34},
    "finances": {"annualIncome": 41000, "monthlyHousingCost": 1000, "existingCreditCommitments": 110},
    "product": {"productCode": "CREDIT_CARD_STANDARD", "requestedCreditLimit": 2400},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected REJECTED — 03 · restricted customer Victor Sable

Expected reason: `POL_CUSTOMER_BLOCKED`.

```json
{
  "applicationId": "POL-DEMO-REJECT-03",
  "correlationId": "pol-demo-reject-0003",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-REJECT-03",
    "channel": "BRANCH",
    "submittedAt": "2026-07-30T09:13:00Z",
    "applicant": {
      "fullName": "Victor Sable",
      "dateOfBirth": "1978-03-02",
      "email": "victor.sable@example.com",
      "mobile": "+447700910013",
      "nationality": "GB",
      "countryOfResidence": "GB",
      "taxResidencies": ["GB"],
      "residentialStatus": "OWNER",
      "currentAddress": {"line1": "6 Regent Avenue", "line2": null, "city": "London", "postcode": "NW1 4NR", "country": "GB"},
      "monthsAtAddress": 96,
      "dependants": 1
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "GBD1000013", "issuingCountry": "GB", "expiryDate": "2030-03-02"},
    "employment": {"status": "PERMANENT", "employerName": "Regent Imports", "monthsInEmployment": 120},
    "finances": {"annualIncome": 72000, "monthlyHousingCost": 1300, "existingCreditCommitments": 240},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 5000},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected REJECTED — 04 · restricted customer Dana Kovacs

Expected reason: `POL_CUSTOMER_BLOCKED`.

```json
{
  "applicationId": "POL-DEMO-REJECT-04",
  "correlationId": "pol-demo-reject-0004",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-REJECT-04",
    "channel": "MOBILE_APP",
    "submittedAt": "2026-07-30T09:14:00Z",
    "applicant": {
      "fullName": "Dana Kovacs",
      "dateOfBirth": "1984-11-19",
      "email": "dana.kovacs@example.com",
      "mobile": "+447700910014",
      "nationality": "HU",
      "countryOfResidence": "GB",
      "taxResidencies": ["GB"],
      "residentialStatus": "MORTGAGE",
      "currentAddress": {"line1": "44 Victoria Road", "line2": null, "city": "Birmingham", "postcode": "B1 3AA", "country": "GB"},
      "monthsAtAddress": 63,
      "dependants": 2
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "HUD1000014", "issuingCountry": "HU", "expiryDate": "2032-11-19"},
    "employment": {"status": "PERMANENT", "employerName": "Central Manufacturing", "monthsInEmployment": 85},
    "finances": {"annualIncome": 56000, "monthlyHousingCost": 1200, "existingCreditCommitments": 190},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 4200},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Expected REJECTED — 05 · unsupported CN residency

Expected reason: `POL_TAX_RESIDENCY_UNSUPPORTED` under seed policy v1.

```json
{
  "applicationId": "POL-DEMO-REJECT-05",
  "correlationId": "pol-demo-reject-0005",
  "command": "process-application",
  "application": {
    "applicationId": "POL-DEMO-REJECT-05",
    "channel": "WEB",
    "submittedAt": "2026-07-30T09:15:00Z",
    "applicant": {
      "fullName": "Chen Wei",
      "dateOfBirth": "1990-01-12",
      "email": "chen.wei@example.com",
      "mobile": "+447700910015",
      "nationality": "CN",
      "countryOfResidence": "GB",
      "taxResidencies": ["CN"],
      "residentialStatus": "RENTING",
      "currentAddress": {"line1": "9 Mill Road", "line2": "Flat 5", "city": "Cambridge", "postcode": "CB1 2AB", "country": "GB"},
      "monthsAtAddress": 19,
      "dependants": 0
    },
    "identityDocument": {"type": "PASSPORT", "documentId": "CND1000015", "issuingCountry": "CN", "expiryDate": "2033-01-12"},
    "employment": {"status": "PERMANENT", "employerName": "Cambridge Data Labs", "monthsInEmployment": 32},
    "finances": {"annualIncome": 48000, "monthlyHousingCost": 1150, "existingCreditCommitments": 140},
    "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 3200},
    "delivery": {"useCurrentAddress": true, "address": null},
    "consents": {"termsAccepted": true, "paperlessStatements": true, "marketingConsent": false}
  }
}
```

## Verify results

发送完成后打开 `http://localhost:5173` 查看 Applications 页面，或调用：

```bash
curl -s 'http://localhost:8080/api/v1/applications?page=0'
```

如果重复执行本文件，请先更换全部 `applicationId`，或者清空本模块的 case 表；相同
`applicationId` 会被当作重试，不会生成新记录。
