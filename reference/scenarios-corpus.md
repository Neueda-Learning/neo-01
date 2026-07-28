# Neo-01 Verification Module — Scenario Corpus

> Auto-generated from sidecar `GET /api/v1/scenarios` (v2.0).
> All 26 test scenarios with full JSON envelopes and expected outcomes.

## Summary

| ID | Scenario | Product | Channel | Expected HTTP | Reason Codes |
|---|---|---|---|---|---|
| SIM-01 | Happy path — rewards card | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_ALL_CHECKS_PASSED, POL_ALL_CHECKS_PASSED, KYC_VERIFIED, SCR_NO_MATCH, CRE_APPROVED, AGR_SIGNED, ACC_OPENED, CRD_ISSUED |
| SIM-02 | Happy path — standard card, second applicant | CREDIT_CARD_STANDARD | WEB | 202 | VER_ALL_CHECKS_PASSED, POL_ALL_CHECKS_PASSED, KYC_VERIFIED, SCR_NO_MATCH, CRE_APPROVED, AGR_SIGNED, ACC_OPENED, CRD_ISSUED |
| SIM-03 | Age boundary — exactly 18 today | CREDIT_CARD_STUDENT | MOBILE_APP | 202 | VER_ALL_CHECKS_PASSED, CRE_APPROVED |
| SIM-04 | Age boundary — one day short of 18 | CREDIT_CARD_STUDENT | MOBILE_APP | 202 | VER_AGE_BELOW_MINIMUM |
| SIM-05 | Limit boundary — exactly the product maximum | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_ALL_CHECKS_PASSED, CRE_APPROVED, CRE_LIMIT_CAPPED_TO_REQUEST |
| SIM-06 | Limit boundary — 500 over the product maximum | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_LIMIT_OUTSIDE_PRODUCT_RANGE, CRE_LIMIT_CAPPED_TO_BAND_MAX |
| SIM-07 | Terms and conditions not accepted | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_TERMS_NOT_ACCEPTED, AGR_PENDING_SIGNATURE |
| SIM-08 | Required fields missing | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_MISSING_FIELD |
| SIM-09 | Field formats invalid | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_INVALID_FIELD |
| SIM-10 | Tax residency on the excluded list (US) | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | POL_TAX_RESIDENCY_EXCLUDED |
| SIM-11 | Tax residency not on the supported list (BR) | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | POL_TAX_RESIDENCY_UNSUPPORTED |
| SIM-12 | Existing customer applying for a second card | CREDIT_CARD_STANDARD | BRANCH | 202 | POL_EXISTING_PRODUCT_HELD, ACC_DUPLICATE_PREVENTED |
| SIM-13 | Identity document expired | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | KYC_DOCUMENT_EXPIRED |
| SIM-14 | ID provider unavailable | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | KYC_PROVIDER_UNAVAILABLE, KYC_FAILED_OVER_TO_SECONDARY |
| SIM-15 | Sanctions list — exact name match | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | SCR_EXACT_MATCH |
| SIM-16 | Sanctions list — partial name match | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | SCR_PARTIAL_MATCH, SCR_CLEARED_BY_ANALYST |
| SIM-17 | High-risk country | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | SCR_HIGH_RISK_COUNTRY |
| SIM-18 | Income below the product minimum | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | CRE_INCOME_BELOW_MINIMUM |
| SIM-19 | Affordability boundary — DTI 0.44 | CREDIT_CARD_STANDARD | MOBILE_APP | 202 | CRE_APPROVED |
| SIM-20 | Affordability boundary — DTI 0.46 | CREDIT_CARD_STANDARD | MOBILE_APP | 202 | CRE_AFFORDABILITY_EXCEEDED |
| SIM-21 | Card delivered to a different address | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | CRD_ISSUED |
| SIM-22 | Alternate delivery requested, no address given | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | CRD_DELIVERY_ADDRESS_INVALID |
| SIM-23 | Unknown fields must be ignored | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | VER_ALL_CHECKS_PASSED |
| SIM-24 | Unknown product code (the b00 demo generator's) | CREDIT_CARD_PREMIUM | MOBILE_APP | 202 | VER_INVALID_FIELD |
| SIM-25 | The same application id, sent twice | CREDIT_CARD_REWARDS | MOBILE_APP | 202 | (none) |
| SIM-26 | Invalid envelope — no application id | CREDIT_CARD_REWARDS | MOBILE_APP | 400 | (none) |

---

## SIM-01 — Happy path — rewards card

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_ALL_CHECKS_PASSED, POL_ALL_CHECKS_PASSED, KYC_VERIFIED, SCR_NO_MATCH, CRE_APPROVED, AGR_SIGNED, ACC_OPENED, CRD_ISSUED
**Note:** Age 30. Income 34 000 >= REWARDS minimum 20 000. Limit 3 000 inside 500-10 000. DTI = (1000 + 180) / (34000 / 12) = 1180 / 2833.33 = 0.417, inside the 0.45 limit.

```json
{
    "applicationId":  "SIM-01",
    "correlationId":  "sim-0001-4c1a-8f2b-1d5e9a000001",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-01",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Maria Nowak",
                                          "dateOfBirth":  "1996-04-11",
                                          "email":  "maria.nowak@example.com",
                                          "mobile":  "+447700900123",
                                          "nationality":  "PL",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "42 Hanbury Street",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "E1 5JP",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  14,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "ZS1234567",
                                                 "issuingCountry":  "PL",
                                                 "expiryDate":  "2031-02-28"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Trellis Health Ltd",
                                           "monthsInEmployment":  11
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-02 — Happy path — standard card, second applicant

**Product:** CREDIT_CARD_STANDARD · **Channel:** WEB
**Expected HTTP:** 202
**Reason Codes:** VER_ALL_CHECKS_PASSED, POL_ALL_CHECKS_PASSED, KYC_VERIFIED, SCR_NO_MATCH, CRE_APPROVED, AGR_SIGNED, ACC_OPENED, CRD_ISSUED
**Note:** Income 26 000 >= STANDARD minimum 12 000. Limit 2 500 inside 250-5 000. DTI = (800 + 120) / (26000 / 12) = 920 / 2166.67 = 0.425.

```json
{
    "applicationId":  "SIM-02",
    "correlationId":  "sim-0002-4c1a-8f2b-1d5e9a000002",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-02",
                        "channel":  "WEB",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Jonas Meyer",
                                          "dateOfBirth":  "1979-02-14",
                                          "email":  "jonas.meyer@example.com",
                                          "mobile":  "+447700900456",
                                          "nationality":  "DE",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "MORTGAGE",
                                          "currentAddress":  {
                                                                 "line1":  "8 Wellington Road",
                                                                 "line2":  null,
                                                                 "city":  "Leeds",
                                                                 "postcode":  "LS1 4DY",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  96,
                                          "dependants":  2
                                      },
                        "identityDocument":  {
                                                 "type":  "DRIVING_LICENCE",
                                                 "documentId":  "MEYER701794JM9AB",
                                                 "issuingCountry":  "GB",
                                                 "expiryDate":  "2029-08-31"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Pennine Foods",
                                           "monthsInEmployment":  140
                                       },
                        "finances":  {
                                         "annualIncome":  26000,
                                         "monthlyHousingCost":  800,
                                         "existingCreditCommitments":  120
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_STANDARD",
                                        "requestedCreditLimit":  2500
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-03 — Age boundary — exactly 18 today

**Product:** CREDIT_CARD_STUDENT · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_ALL_CHECKS_PASSED, CRE_APPROVED
**Note:** Date of birth is 2008-07-28, so the applicant is exactly 18 on the day you send it — this scenario can no longer age out. STUDENT minimum income is 0. DTI = 200 / (9000 / 12) = 0.267.

```json
{
    "applicationId":  "SIM-03",
    "correlationId":  "sim-0003-4c1a-8f2b-1d5e9a000003",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-03",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-28T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Amara Osei",
                                          "dateOfBirth":  "2008-07-28",
                                          "email":  "amara.osei@example.com",
                                          "mobile":  "+447700900201",
                                          "nationality":  "GB",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "LIVING_WITH_FAMILY",
                                          "currentAddress":  {
                                                                 "line1":  "17 Blenheim Terrace",
                                                                 "line2":  null,
                                                                 "city":  "Leeds",
                                                                 "postcode":  "LS2 9JT",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  120,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "GB9004411",
                                                 "issuingCountry":  "GB",
                                                 "expiryDate":  "2033-05-19"
                                             },
                        "employment":  {
                                           "status":  "STUDENT",
                                           "employerName":  "University of Leeds",
                                           "monthsInEmployment":  0
                                       },
                        "finances":  {
                                         "annualIncome":  9000,
                                         "monthlyHousingCost":  200,
                                         "existingCreditCommitments":  0
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_STUDENT",
                                        "requestedCreditLimit":  750
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-04 — Age boundary — one day short of 18

**Product:** CREDIT_CARD_STUDENT · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_AGE_BELOW_MINIMUM
**Note:** Date of birth is 2008-07-29 — one day too young, so 17 years 364 days on the day you send it. Paired with scenario 03: together they are both sides of the boundary, and neither drifts. (Before the tokens existed these were fixed dates, and scenario 04 quietly turned 18 the day after it was written.)

```json
{
    "applicationId":  "SIM-04",
    "correlationId":  "sim-0004-4c1a-8f2b-1d5e9a000004",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-04",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-28T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Liam Doyle",
                                          "dateOfBirth":  "2008-07-29",
                                          "email":  "liam.doyle@example.com",
                                          "mobile":  "+447700900202",
                                          "nationality":  "IE",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "LIVING_WITH_FAMILY",
                                          "currentAddress":  {
                                                                 "line1":  "4 Redcliffe Parade",
                                                                 "line2":  null,
                                                                 "city":  "Bristol",
                                                                 "postcode":  "BS1 6QF",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  60,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "IE7712304",
                                                 "issuingCountry":  "IE",
                                                 "expiryDate":  "2032-03-14"
                                             },
                        "employment":  {
                                           "status":  "STUDENT",
                                           "employerName":  "Harbour Analytics",
                                           "monthsInEmployment":  0
                                       },
                        "finances":  {
                                         "annualIncome":  9000,
                                         "monthlyHousingCost":  200,
                                         "existingCreditCommitments":  0
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_STUDENT",
                                        "requestedCreditLimit":  750
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-05 — Limit boundary — exactly the product maximum

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_ALL_CHECKS_PASSED, CRE_APPROVED, CRE_LIMIT_CAPPED_TO_REQUEST
**Note:** REWARDS band is 500-10 000; 10 000 is inside it. DTI = (1200 + 300) / (60000 / 12) = 1500 / 5000 = 0.300.

```json
{
    "applicationId":  "SIM-05",
    "correlationId":  "sim-0005-4c1a-8f2b-1d5e9a000005",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-05",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Priya Raman",
                                          "dateOfBirth":  "1988-06-03",
                                          "email":  "priya.raman@example.com",
                                          "mobile":  "+447700900203",
                                          "nationality":  "IN",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "OWNER",
                                          "currentAddress":  {
                                                                 "line1":  "12 Dale Street",
                                                                 "line2":  null,
                                                                 "city":  "Manchester",
                                                                 "postcode":  "M1 4BT",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  72,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "IN5540982",
                                                 "issuingCountry":  "IN",
                                                 "expiryDate":  "2030-11-02"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Northgate Logistics",
                                           "monthsInEmployment":  84
                                       },
                        "finances":  {
                                         "annualIncome":  60000,
                                         "monthlyHousingCost":  1200,
                                         "existingCreditCommitments":  300
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  10000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-06 — Limit boundary — 500 over the product maximum

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_LIMIT_OUTSIDE_PRODUCT_RANGE, CRE_LIMIT_CAPPED_TO_BAND_MAX
**Note:** Two modules answer this differently and both are right: verification rejects it as outside 500-10 000, credit caps it to 10 000. That is ground rule 3 — steps are independent and never read each other's result.

```json
{
    "applicationId":  "SIM-06",
    "correlationId":  "sim-0006-4c1a-8f2b-1d5e9a000006",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-06",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Stefan Bauer",
                                          "dateOfBirth":  "1986-09-21",
                                          "email":  "stefan.bauer@example.com",
                                          "mobile":  "+447700900204",
                                          "nationality":  "AT",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "OWNER",
                                          "currentAddress":  {
                                                                 "line1":  "3 Arundel Gate",
                                                                 "line2":  null,
                                                                 "city":  "Sheffield",
                                                                 "postcode":  "S1 2HE",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  48,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "AT3391027",
                                                 "issuingCountry":  "AT",
                                                 "expiryDate":  "2030-04-18"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Steel City Media",
                                           "monthsInEmployment":  60
                                       },
                        "finances":  {
                                         "annualIncome":  60000,
                                         "monthlyHousingCost":  1200,
                                         "existingCreditCommitments":  300
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  10500
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-07 — Terms and conditions not accepted

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_TERMS_NOT_ACCEPTED, AGR_PENDING_SIGNATURE
**Note:** consents.termsAccepted is false. Module 1 rejects on it; module 6 cannot raise an agreement without it.

```json
{
    "applicationId":  "SIM-07",
    "correlationId":  "sim-0007-4c1a-8f2b-1d5e9a000007",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-07",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Elena Varga",
                                          "dateOfBirth":  "1994-12-05",
                                          "email":  "elena.varga@example.com",
                                          "mobile":  "+447700900205",
                                          "nationality":  "HU",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "22 Bute Street",
                                                                 "line2":  null,
                                                                 "city":  "Cardiff",
                                                                 "postcode":  "CF10 1EP",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  30,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "HU8820113",
                                                 "issuingCountry":  "HU",
                                                 "expiryDate":  "2029-12-01"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Severn Utilities",
                                           "monthsInEmployment":  36
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  false,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-08 — Required fields missing

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_MISSING_FIELD
**Note:** Null, not empty string — contract §3: optional fields are omitted or null, never "" or 0. Your module must say WHICH field is missing in the reason detail.

```json
{
    "applicationId":  "SIM-08",
    "correlationId":  "sim-0008-4c1a-8f2b-1d5e9a000008",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-08",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Marcus Bell",
                                          "dateOfBirth":  "1990-08-17",
                                          "email":  null,
                                          "mobile":  null,
                                          "nationality":  "GB",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "9 Duke Street",
                                                                 "line2":  null,
                                                                 "city":  "Liverpool",
                                                                 "postcode":  null,
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  20,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "GB4471902",
                                                 "issuingCountry":  "GB",
                                                 "expiryDate":  "2031-07-09"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  null,
                                           "monthsInEmployment":  24
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-09 — Field formats invalid

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_INVALID_FIELD
**Note:** Five format breaks: date of birth DD/MM/YYYY not YYYY-MM-DD, email has no @, mobile is not E.164, country codes are alpha-3 and lower case not ISO alpha-2 upper, expiry date reversed. Contract §3 ground rules on values.

```json
{
    "applicationId":  "SIM-09",
    "correlationId":  "sim-0009-4c1a-8f2b-1d5e9a000009",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-09",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Ines Da Costa",
                                          "dateOfBirth":  "12/03/1990",
                                          "email":  "ines.dacosta(at)example.com",
                                          "mobile":  "07700 900 999",
                                          "nationality":  "PRT",
                                          "countryOfResidence":  "gbr",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "5 Bothwell Street",
                                                                 "line2":  null,
                                                                 "city":  "Glasgow",
                                                                 "postcode":  "g2 1du",
                                                                 "country":  "gbr"
                                                             },
                                          "monthsAtAddress":  40,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "PT2210984",
                                                 "issuingCountry":  "PRT",
                                                 "expiryDate":  "31-12-2030"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Clyde Robotics",
                                           "monthsInEmployment":  50
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-10 — Tax residency on the excluded list (US)

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** POL_TAX_RESIDENCY_EXCLUDED
**Note:** Contract §3: excluded list is US. Note taxResidencies is an array — a module that only reads the first entry passes this one by mistake. DTI = (1400 + 200) / (52000 / 12) = 1600 / 4333.33 = 0.369.

```json
{
    "applicationId":  "SIM-10",
    "correlationId":  "sim-0010-4c1a-8f2b-1d5e9a000010",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-10",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Grace Miller",
                                          "dateOfBirth":  "1983-04-29",
                                          "email":  "grace.miller@example.com",
                                          "mobile":  "+447700900207",
                                          "nationality":  "US",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB",
                                                                 "US"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "31 Marsham Street",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "SW1P 3BT",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  26,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "US7719023",
                                                 "issuingCountry":  "US",
                                                 "expiryDate":  "2032-01-22"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Atlantic Reinsurance",
                                           "monthsInEmployment":  44
                                       },
                        "finances":  {
                                         "annualIncome":  52000,
                                         "monthlyHousingCost":  1400,
                                         "existingCreditCommitments":  200
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-11 — Tax residency not on the supported list (BR)

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** POL_TAX_RESIDENCY_UNSUPPORTED
**Note:** Supported list is GB, IE, PL, DE, FR, ES, NL. BR is on neither list, which is not the same as being on the excluded one — two reason codes, two situations.

```json
{
    "applicationId":  "SIM-11",
    "correlationId":  "sim-0011-4c1a-8f2b-1d5e9a000011",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-11",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Rafael Santos",
                                          "dateOfBirth":  "1991-10-12",
                                          "email":  "rafael.santos@example.com",
                                          "mobile":  "+447700900208",
                                          "nationality":  "BR",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "BR"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "18 Temple Way",
                                                                 "line2":  null,
                                                                 "city":  "Bristol",
                                                                 "postcode":  "BS2 0FZ",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  18,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "BR6640281",
                                                 "issuingCountry":  "BR",
                                                 "expiryDate":  "2030-06-30"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Harbour Analytics",
                                           "monthsInEmployment":  22
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-12 — Existing customer applying for a second card

**Product:** CREDIT_CARD_STANDARD · **Channel:** BRANCH
**Expected HTTP:** 202
**Reason Codes:** POL_EXISTING_PRODUCT_HELD, ACC_DUPLICATE_PREVENTED
**Note:** Send SIM-01 first, then this one. Module 2 must key its one-card-per-customer rule on the person, not on applicationId — this scenario is the only thing that tells you whether it does.

```json
{
    "applicationId":  "SIM-12",
    "correlationId":  "sim-0012-4c1a-8f2b-1d5e9a000012",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-12",
                        "channel":  "BRANCH",
                        "submittedAt":  "2026-07-25T14:02:00Z",
                        "applicant":  {
                                          "fullName":  "Maria Nowak",
                                          "dateOfBirth":  "1996-04-11",
                                          "email":  "maria.nowak@example.com",
                                          "mobile":  "+447700900123",
                                          "nationality":  "PL",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "42 Hanbury Street",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "E1 5JP",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  14,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "ZS1234567",
                                                 "issuingCountry":  "PL",
                                                 "expiryDate":  "2031-02-28"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Trellis Health Ltd",
                                           "monthsInEmployment":  11
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_STANDARD",
                                        "requestedCreditLimit":  1500
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-13 — Identity document expired

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** KYC_DOCUMENT_EXPIRED
**Note:** Expiry 2025-11-30, a literal date on purpose: a document that has expired stays expired, so this one cannot drift and does not need a token. Your mock ID provider should not need to be called to answer it — the date is enough.

```json
{
    "applicationId":  "SIM-13",
    "correlationId":  "sim-0013-4c1a-8f2b-1d5e9a000013",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-13",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Henrik Larsen",
                                          "dateOfBirth":  "1987-02-08",
                                          "email":  "henrik.larsen@example.com",
                                          "mobile":  "+447700900209",
                                          "nationality":  "DK",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "6 Grey Street",
                                                                 "line2":  null,
                                                                 "city":  "Newcastle",
                                                                 "postcode":  "NE1 4ST",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  55,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "DK1180552",
                                                 "issuingCountry":  "DK",
                                                 "expiryDate":  "2025-11-30"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Tyne Renewables",
                                           "monthsInEmployment":  70
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-14 — ID provider unavailable

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** KYC_PROVIDER_UNAVAILABLE, KYC_FAILED_OVER_TO_SECONDARY
**Note:** Corpus convention: a documentId of ZZ0000000 means 'make your mock provider fail'. Wire your mock to time out or 503 on it. Module 3 exists to teach a failure mode — this is the input that lets you demonstrate it without unplugging anything.

```json
{
    "applicationId":  "SIM-14",
    "correlationId":  "sim-0014-4c1a-8f2b-1d5e9a000014",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-14",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Yusuf Demir",
                                          "dateOfBirth":  "1992-05-23",
                                          "email":  "yusuf.demir@example.com",
                                          "mobile":  "+447700900210",
                                          "nationality":  "TR",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "44 Broad Street",
                                                                 "line2":  null,
                                                                 "city":  "Birmingham",
                                                                 "postcode":  "B1 1TF",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  34,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "ZZ0000000",
                                                 "issuingCountry":  "TR",
                                                 "expiryDate":  "2031-09-15"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Midlands Freight",
                                           "monthsInEmployment":  41
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-15 — Sanctions list — exact name match

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** SCR_EXACT_MATCH
**Note:** Corpus convention: Viktor Petrov, born 1975-05-14, is the planted exact hit. Put him on your mock watchlist. DTI = (2200 + 400) / (88000 / 12) = 2600 / 7333.33 = 0.355 — affordable, so only screening should stop this one.

```json
{
    "applicationId":  "SIM-15",
    "correlationId":  "sim-0015-4c1a-8f2b-1d5e9a000015",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-15",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Viktor Petrov",
                                          "dateOfBirth":  "1975-05-14",
                                          "email":  "viktor.petrov@example.com",
                                          "mobile":  "+447700900211",
                                          "nationality":  "RU",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "27 Curzon Street",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "W1J 7NT",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  8,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "RU9930118",
                                                 "issuingCountry":  "RU",
                                                 "expiryDate":  "2029-03-27"
                                             },
                        "employment":  {
                                           "status":  "SELF_EMPLOYED",
                                           "employerName":  "Baltic Trade Partners",
                                           "monthsInEmployment":  96
                                       },
                        "finances":  {
                                         "annualIncome":  88000,
                                         "monthlyHousingCost":  2200,
                                         "existingCreditCommitments":  400
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  9000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-16 — Sanctions list — partial name match

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** SCR_PARTIAL_MATCH, SCR_CLEARED_BY_ANALYST
**Note:** Same stem as scenario 15, different first name and a different date of birth. A partial match is a referral for a human to clear, not a rejection — this is the pair that shows your matching is fuzzy but your decision is not.

```json
{
    "applicationId":  "SIM-16",
    "correlationId":  "sim-0016-4c1a-8f2b-1d5e9a000016",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-16",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Viktoria Petrova",
                                          "dateOfBirth":  "1982-01-09",
                                          "email":  "viktoria.petrova@example.com",
                                          "mobile":  "+447700900212",
                                          "nationality":  "BG",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "15 Thomas Street",
                                                                 "line2":  null,
                                                                 "city":  "Manchester",
                                                                 "postcode":  "M4 1HN",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  62,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "BG4402117",
                                                 "issuingCountry":  "BG",
                                                 "expiryDate":  "2031-10-05"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Pennine Foods",
                                           "monthsInEmployment":  88
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-17 — High-risk country

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** SCR_HIGH_RISK_COUNTRY
**Note:** nationality and identityDocument.issuingCountry are IR while countryOfResidence is GB. Which field your rule reads is a design decision — write it down.

```json
{
    "applicationId":  "SIM-17",
    "correlationId":  "sim-0017-4c1a-8f2b-1d5e9a000017",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-17",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Reza Karimi",
                                          "dateOfBirth":  "1984-07-30",
                                          "email":  "reza.karimi@example.com",
                                          "mobile":  "+447700900213",
                                          "nationality":  "IR",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "10 Belvoir Street",
                                                                 "line2":  null,
                                                                 "city":  "Leicester",
                                                                 "postcode":  "LE1 6TE",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  15,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "IR7761209",
                                                 "issuingCountry":  "IR",
                                                 "expiryDate":  "2030-02-11"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Midlands Freight",
                                           "monthsInEmployment":  29
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-18 — Income below the product minimum

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** CRE_INCOME_BELOW_MINIMUM
**Note:** REWARDS minimum income is 20 000; this is 18 000. Affordability is fine: DTI = (500 + 50) / (18000 / 12) = 550 / 1500 = 0.367. A module that only checks DTI lets this through.

```json
{
    "applicationId":  "SIM-18",
    "correlationId":  "sim-0018-4c1a-8f2b-1d5e9a000018",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-18",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Chloe Adams",
                                          "dateOfBirth":  "1997-11-19",
                                          "email":  "chloe.adams@example.com",
                                          "mobile":  "+447700900214",
                                          "nationality":  "GB",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "7 Fletcher Gate",
                                                                 "line2":  null,
                                                                 "city":  "Nottingham",
                                                                 "postcode":  "NG1 5FS",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  11,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "GB5518830",
                                                 "issuingCountry":  "GB",
                                                 "expiryDate":  "2032-08-04"
                                             },
                        "employment":  {
                                           "status":  "CONTRACT",
                                           "employerName":  "Trent Retail Group",
                                           "monthsInEmployment":  9
                                       },
                        "finances":  {
                                         "annualIncome":  18000,
                                         "monthlyHousingCost":  500,
                                         "existingCreditCommitments":  50
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  1500
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-19 — Affordability boundary — DTI 0.44

**Product:** CREDIT_CARD_STANDARD · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** CRE_APPROVED
**Note:** DTI = (1000 + 100) / (30000 / 12) = 1100 / 2500 = 0.440 <= 0.45. Income 30 000 clears the STANDARD floor of 12 000.

```json
{
    "applicationId":  "SIM-19",
    "correlationId":  "sim-0019-4c1a-8f2b-1d5e9a000019",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-19",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Daniel Fischer",
                                          "dateOfBirth":  "1989-03-23",
                                          "email":  "daniel.fischer@example.com",
                                          "mobile":  "+447700900215",
                                          "nationality":  "DE",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "21 Norfolk Row",
                                                                 "line2":  null,
                                                                 "city":  "Sheffield",
                                                                 "postcode":  "S1 4RG",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  39,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "DE2290471",
                                                 "issuingCountry":  "DE",
                                                 "expiryDate":  "2031-01-16"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Steel City Media",
                                           "monthsInEmployment":  65
                                       },
                        "finances":  {
                                         "annualIncome":  30000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  100
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_STANDARD",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-20 — Affordability boundary — DTI 0.46

**Product:** CREDIT_CARD_STANDARD · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** CRE_AFFORDABILITY_EXCEEDED
**Note:** DTI = (1000 + 150) / (30000 / 12) = 1150 / 2500 = 0.460 > 0.45. The 50-pound difference from scenario 19 is the whole test: rounding or > vs >= shows up here.

```json
{
    "applicationId":  "SIM-20",
    "correlationId":  "sim-0020-4c1a-8f2b-1d5e9a000020",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-20",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Nadia Haddad",
                                          "dateOfBirth":  "1990-06-14",
                                          "email":  "nadia.haddad@example.com",
                                          "mobile":  "+447700900216",
                                          "nationality":  "FR",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "3 Hertford Street",
                                                                 "line2":  null,
                                                                 "city":  "Coventry",
                                                                 "postcode":  "CV1 5FB",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  27,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "FR3308814",
                                                 "issuingCountry":  "FR",
                                                 "expiryDate":  "2030-09-08"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Midlands Freight",
                                           "monthsInEmployment":  58
                                       },
                        "finances":  {
                                         "annualIncome":  30000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  150
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_STANDARD",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-21 — Card delivered to a different address

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** CRD_ISSUED
**Note:** delivery.useCurrentAddress is false, so module 8 must post to delivery.address and not to applicant.currentAddress. DTI = 1500 / (47000 / 12) = 1500 / 3916.67 = 0.383.

```json
{
    "applicationId":  "SIM-21",
    "correlationId":  "sim-0021-4c1a-8f2b-1d5e9a000021",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-21",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Oscar Lindqvist",
                                          "dateOfBirth":  "1985-12-02",
                                          "email":  "oscar.lindqvist@example.com",
                                          "mobile":  "+447700900217",
                                          "nationality":  "SE",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "88 Curtain Road",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "EC2A 4NE",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  4,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "SE7719044",
                                                 "issuingCountry":  "SE",
                                                 "expiryDate":  "2032-05-25"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Baltic Trade Partners",
                                           "monthsInEmployment":  33
                                       },
                        "finances":  {
                                         "annualIncome":  47000,
                                         "monthlyHousingCost":  1500,
                                         "existingCreditCommitments":  0
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  false,
                                         "address":  {
                                                         "line1":  "Baltic Trade Partners",
                                                         "line2":  "4th Floor, 120 Moorgate",
                                                         "city":  "London",
                                                         "postcode":  "EC2M 6UR",
                                                         "country":  "GB"
                                                     }
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-22 — Alternate delivery requested, no address given

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** CRD_DELIVERY_ADDRESS_INVALID
**Note:** The pair to scenario 21. A module that reads delivery.address without checking for null throws here instead of answering, and a thrown module never calls back.

```json
{
    "applicationId":  "SIM-22",
    "correlationId":  "sim-0022-4c1a-8f2b-1d5e9a000022",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-22",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Ruth Kelly",
                                          "dateOfBirth":  "1993-09-06",
                                          "email":  "ruth.kelly@example.com",
                                          "mobile":  "+447700900218",
                                          "nationality":  "IE",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "12 Donegall Square",
                                                                 "line2":  null,
                                                                 "city":  "Belfast",
                                                                 "postcode":  "BT1 5GS",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  51,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "IE9930277",
                                                 "issuingCountry":  "IE",
                                                 "expiryDate":  "2031-04-12"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Lagan Health",
                                           "monthsInEmployment":  77
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  false,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-23 — Unknown fields must be ignored

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_ALL_CHECKS_PASSED
**Note:** Contract §3: unknown fields are ignored on the way in and never emitted on the way out. This scenario passes only if your parsing does not fail on campaignCode, referrer, applicant.middleName and product.promoRate. It is how the contract grows without breaking you.

```json
{
    "applicationId":  "SIM-23",
    "correlationId":  "sim-0023-4c1a-8f2b-1d5e9a000023",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-23",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Ana Ruiz",
                                          "dateOfBirth":  "1993-06-27",
                                          "email":  "ana.ruiz@example.com",
                                          "mobile":  "+447700900219",
                                          "nationality":  "ES",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "2 Anchor Road",
                                                                 "line2":  null,
                                                                 "city":  "Bristol",
                                                                 "postcode":  "BS1 6QF",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  44,
                                          "dependants":  0,
                                          "middleName":  "Isabel"
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "ES2214760",
                                                 "issuingCountry":  "ES",
                                                 "expiryDate":  "2030-12-19"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Harbour Analytics",
                                           "monthsInEmployment":  63
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000,
                                        "promoRate":  19.9
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     },
                        "campaignCode":  "SUMMER26",
                        "referrer":  "aggregator-partner-7"
                    }
}
```

---

## SIM-24 — Unknown product code (the b00 demo generator's)

**Product:** CREDIT_CARD_PREMIUM · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** VER_INVALID_FIELD
**Note:** Tracked drift: attempt-b00's demo generator emits CREDIT_CARD_PREMIUM and CREDIT_CARD_PLATINUM; the locked catalogue in api-contract.md §3 has STANDARD, REWARDS and STUDENT. Your module must not crash on a code it does not know. Decide what it does and write it down — referring for a human is a defensible answer.

```json
{
    "applicationId":  "SIM-24",
    "correlationId":  "sim-0024-4c1a-8f2b-1d5e9a000024",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-24",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Peter Novak",
                                          "dateOfBirth":  "1985-01-19",
                                          "email":  "peter.novak@example.com",
                                          "mobile":  "+447700900220",
                                          "nationality":  "SK",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "5 Churchill Way",
                                                                 "line2":  null,
                                                                 "city":  "Cardiff",
                                                                 "postcode":  "CF10 2EH",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  88,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "SK6612903",
                                                 "issuingCountry":  "SK",
                                                 "expiryDate":  "2029-07-21"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Severn Utilities",
                                           "monthsInEmployment":  110
                                       },
                        "finances":  {
                                         "annualIncome":  45000,
                                         "monthlyHousingCost":  1100,
                                         "existingCreditCommitments":  250
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_PREMIUM",
                                        "requestedCreditLimit":  7500
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-25 — The same application id, sent twice

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 202
**Reason Codes:** (none)
**Note:** Send scenario 01 first. This is a re-send of the SAME applicationId: the module must answer 202 again and must NOT create a second row (RequestService.receive reuses the existing one). Watch what happens to the stored payload — the template keeps the first one. Decide whether that is what you want.

```json
{
    "applicationId":  "SIM-01",
    "correlationId":  "sim-0025-4c1a-8f2b-1d5e9a000025",
    "command":  "process-application",
    "application":  {
                        "applicationId":  "SIM-01",
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:19:00Z",
                        "applicant":  {
                                          "fullName":  "Maria Nowak",
                                          "dateOfBirth":  "1996-04-11",
                                          "email":  "maria.nowak@example.com",
                                          "mobile":  "+447700900123",
                                          "nationality":  "PL",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "42 Hanbury Street",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "E1 5JP",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  14,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "ZS1234567",
                                                 "issuingCountry":  "PL",
                                                 "expiryDate":  "2031-02-28"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Trellis Health Ltd",
                                           "monthsInEmployment":  11
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  4000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---

## SIM-26 — Invalid envelope — no application id

**Product:** CREDIT_CARD_REWARDS · **Channel:** MOBILE_APP
**Expected HTTP:** 400
**Reason Codes:** (none)
**Note:** The only scenario that must NOT return 202. @NotBlank on ApplicationRequest.applicationId makes this a 400 with the error shape from ApiExceptionHandler, and nothing is written to the database. If your module answers 202 here, your validation is gone.

```json
{
    "correlationId":  "sim-0026-4c1a-8f2b-1d5e9a000026",
    "command":  "process-application",
    "application":  {
                        "channel":  "MOBILE_APP",
                        "submittedAt":  "2026-07-25T09:14:00Z",
                        "applicant":  {
                                          "fullName":  "Maria Nowak",
                                          "dateOfBirth":  "1996-04-11",
                                          "email":  "maria.nowak@example.com",
                                          "mobile":  "+447700900123",
                                          "nationality":  "PL",
                                          "countryOfResidence":  "GB",
                                          "taxResidencies":  [
                                                                 "GB"
                                                             ],
                                          "residentialStatus":  "RENTING",
                                          "currentAddress":  {
                                                                 "line1":  "42 Hanbury Street",
                                                                 "line2":  null,
                                                                 "city":  "London",
                                                                 "postcode":  "E1 5JP",
                                                                 "country":  "GB"
                                                             },
                                          "monthsAtAddress":  14,
                                          "dependants":  0
                                      },
                        "identityDocument":  {
                                                 "type":  "PASSPORT",
                                                 "documentId":  "ZS1234567",
                                                 "issuingCountry":  "PL",
                                                 "expiryDate":  "2031-02-28"
                                             },
                        "employment":  {
                                           "status":  "PERMANENT",
                                           "employerName":  "Trellis Health Ltd",
                                           "monthsInEmployment":  11
                                       },
                        "finances":  {
                                         "annualIncome":  34000,
                                         "monthlyHousingCost":  1000,
                                         "existingCreditCommitments":  180
                                     },
                        "product":  {
                                        "productCode":  "CREDIT_CARD_REWARDS",
                                        "requestedCreditLimit":  3000
                                    },
                        "delivery":  {
                                         "useCurrentAddress":  true,
                                         "address":  null
                                     },
                        "consents":  {
                                         "termsAccepted":  true,
                                         "paperlessStatements":  true,
                                         "marketingConsent":  false
                                     }
                    }
}
```

---


