# FinCalc

A small, ad-free Android finance calculator in a single Java file — no AndroidX, no build dependencies beyond the Android SDK.

## Features

- **Loan / Interest Calculator** — loan amount, annual interest rate and period (years + months), with two modes:
  - EMI (reducing balance): monthly EMI, total interest, total payable
  - Simple interest: monthly payment, total interest, total payable
  - Month-by-month amortization schedule (payment split into principal + interest, with remaining balance)
  - **Installment Plans** card: enter an amount and see 1 / 3 / 6-month installment options (monthly installment, total, extra cost). Built-in rates match CashNow-style pricing (1-month: 4% flat fee; 3-month: 4.44%/month; 6-month: 6.26%/month) and all three rates are editable in the app.
- **Percentage Calculator** — X% of Y, "X is what % of Y", and percentage increase/decrease between two values
- **Live Currency Converter** — 25 currencies (AED, INR, USD, EUR, GBP, SAR, QAR, KWD…) with rates from [open.er-api.com](https://open.er-api.com), swap button, and offline caching of the last fetched rates
- **Live Gold Rates** — spot gold from [gold-api.com](https://gold-api.com): per-gram prices for 24K / 22K / 21K / 18K, per-ounce and per-tola (24K), in a selectable currency, plus a grams + karat value calculator. Last price is cached for offline use.

Requires Android 13 (API 33) or newer. Internet is used only for the currency and gold rates.

## Build

No Gradle needed — `build.sh` compiles with `javac`, `aapt2` and `d8` from the Android SDK build-tools:

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK=/path/to/android-sdk   # needs platforms/android-34 and build-tools/34.0.0
./build.sh
```

Output: `FinCalc.apk` (signed with a debug key; installable via sideload).

## Layout

```
AndroidManifest.xml
build.sh
res/drawable/ic_launcher.xml
src/com/noufal/fincalc/MainActivity.java   ← the whole app
```
