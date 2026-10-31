package com.noufal.fincalc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int TEAL = Color.rgb(15, 118, 110);
    private static final int TEAL_DARK = Color.rgb(11, 84, 79);
    private static final int BG = Color.rgb(243, 246, 245);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(28, 32, 31);
    private static final int MUTED = Color.rgb(102, 112, 110);

    private FrameLayout content;
    private View loanView, percentView, currencyView, goldView, scheduleView;
    private Button tabLoan, tabPercent, tabCurrency, tabGold;

    // Last loan calculation (for the schedule)
    private double lastPrincipal, lastRateAnnual, lastMonthlyPay, lastTotalInterest;
    private int lastMonths;
    private boolean lastIsEmi, hasCalc;

    // Gold tab fields
    private EditText etGoldGrams;
    private Spinner spGoldCurrency, spGoldKarat;
    private TextView tvGoldPrices, tvGoldResult, tvGoldUpdated;
    private double goldUsdPerOz = -1;

    // Loan tab fields
    private EditText etAmount, etRate, etYears, etMonths;
    private Spinner spLoanType;
    private TextView tvLoanResult;

    // Installment-plan fields
    private EditText etPlanAmount, etPlanFee1, etPlanRate3, etPlanRate6;
    private TextView tvPlanResult;

    // Percent tab fields
    private EditText etP1Percent, etP1Of, etP2Value, etP2Total, etP3From, etP3To;
    private TextView tvP1Result, tvP2Result, tvP3Result;

    // Currency tab fields
    private EditText etCurAmount;
    private Spinner spFrom, spTo;
    private TextView tvCurResult, tvCurRate, tvCurUpdated;
    private JSONObject ratesJson = null;
    private boolean ratesLive = false;

    private static final String[] CURRENCIES = {
        "AED", "INR", "USD", "EUR", "GBP", "SAR", "QAR", "KWD", "OMR", "BHD",
        "PKR", "BDT", "LKR", "NPR", "PHP", "MYR", "SGD", "JPY", "CNY", "AUD",
        "CAD", "CHF", "ZAR", "EGP", "TRY"
    };

    private final NumberFormat fmt2 = new DecimalFormat("#,##0.00");
    private final NumberFormat fmtRate = new DecimalFormat("#,##0.00####");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView header = new TextView(this);
        header.setText("FinCalc");
        header.setTextSize(22);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(TEAL);
        root.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(8), dp(8), dp(4));
        tabLoan = tabButton("Loan");
        tabPercent = tabButton("Percent");
        tabCurrency = tabButton("Currency");
        tabGold = tabButton("Gold");
        tabs.addView(tabLoan, weightParams());
        tabs.addView(tabPercent, weightParams());
        tabs.addView(tabCurrency, weightParams());
        tabs.addView(tabGold, weightParams());
        root.addView(tabs);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loanView = buildLoanTab();
        percentView = buildPercentTab();
        currencyView = buildCurrencyTab();
        goldView = buildGoldTab();
        scheduleView = buildScheduleView();
        content.addView(loanView);
        content.addView(percentView);
        content.addView(currencyView);
        content.addView(goldView);
        content.addView(scheduleView);

        tabLoan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(0); }
        });
        tabPercent.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(1); }
        });
        tabCurrency.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(2); }
        });
        tabGold.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(3); }
        });

        setContentView(root);
        showTab(0);
        loadCachedRates();
        loadCachedGold();
        fetchRates();
        fetchGold();
    }

    // ---------- UI helpers ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private Button tabButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private void styleTab(Button b, boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(active ? TEAL : Color.rgb(224, 231, 230));
        b.setBackground(d);
        b.setTextColor(active ? Color.WHITE : TEXT);
    }

    private void showTab(int which) {
        loanView.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
        percentView.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
        currencyView.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        goldView.setVisibility(which == 3 ? View.VISIBLE : View.GONE);
        scheduleView.setVisibility(View.GONE);
        styleTab(tabLoan, which == 0);
        styleTab(tabPercent, which == 1);
        styleTab(tabCurrency, which == 2);
        styleTab(tabGold, which == 3);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(CARD);
        c.setBackground(d);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(12), dp(8), dp(12), dp(8));
        c.setLayoutParams(p);
        return c;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView cardTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TEXT);
        t.setPadding(0, 0, 0, dp(2));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(17);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setSingleLine(true);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        return e;
    }

    private Button actionButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(TEAL);
        b.setBackground(d);
        b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(14), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private TextView resultText() {
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setTextColor(TEXT);
        t.setPadding(0, dp(14), 0, 0);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    private ScrollView scrollWrap(LinearLayout inner) {
        ScrollView s = new ScrollView(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(4), 0, dp(16));
        wrap.addView(inner);
        s.addView(wrap);
        return s;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private Double parse(EditText e) {
        try {
            String s = e.getText().toString().trim().replace(",", "");
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- Loan tab ----------

    private View buildLoanTab() {
        LinearLayout col = column();

        LinearLayout c1 = card();
        c1.addView(cardTitle("Loan / Interest Calculator"));
        c1.addView(label("Loan amount"));
        etAmount = input("e.g. 100000");
        c1.addView(etAmount);
        c1.addView(label("Interest rate (% per year)"));
        etRate = input("e.g. 8.5");
        c1.addView(etRate);

        LinearLayout period = new LinearLayout(this);
        period.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout yCol = column();
        yCol.addView(label("Years"));
        etYears = input("5");
        yCol.addView(etYears);
        LinearLayout mCol = column();
        mCol.addView(label("Months"));
        etMonths = input("0");
        mCol.addView(etMonths);
        period.addView(yCol, weightParams());
        period.addView(mCol, weightParams());
        c1.addView(period);

        c1.addView(label("Calculation type"));
        spLoanType = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"EMI (reducing balance)", "Simple interest"});
        spLoanType.setAdapter(ad);
        c1.addView(spLoanType);

        Button calc = actionButton("Calculate");
        calc.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { calculateLoan(); }
        });
        c1.addView(calc);

        tvLoanResult = resultText();
        c1.addView(tvLoanResult);

        Button sched = actionButton("View month-by-month schedule");
        sched.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!hasCalc) {
                    toast("Calculate a loan first");
                    return;
                }
                showSchedule();
            }
        });
        c1.addView(sched);
        col.addView(c1);

        LinearLayout c2 = card();
        c2.addView(cardTitle("Installment Plans"));
        c2.addView(label("Loan amount"));
        etPlanAmount = input("e.g. 1500");
        c2.addView(etPlanAmount);
        c2.addView(label("1-month plan fee (%)"));
        etPlanFee1 = input("4");
        etPlanFee1.setText("4");
        c2.addView(etPlanFee1);
        c2.addView(label("3-month plan rate (% per month)"));
        etPlanRate3 = input("4.44");
        etPlanRate3.setText("4.44");
        c2.addView(etPlanRate3);
        c2.addView(label("6-month plan rate (% per month)"));
        etPlanRate6 = input("6.26");
        etPlanRate6.setText("6.26");
        c2.addView(etPlanRate6);
        Button plans = actionButton("Show installment options");
        plans.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { calculatePlans(); }
        });
        c2.addView(plans);
        tvPlanResult = resultText();
        c2.addView(tvPlanResult);
        col.addView(c2);
        return scrollWrap(col);
    }

    private double emiOf(double p, double monthlyRatePct, int months) {
        double r = monthlyRatePct / 100.0;
        if (r == 0) return p / months;
        double f = Math.pow(1 + r, months);
        return p * r * f / (f - 1);
    }

    private void calculatePlans() {
        Double p = parse(etPlanAmount);
        Double fee1 = parse(etPlanFee1);
        Double r3 = parse(etPlanRate3);
        Double r6 = parse(etPlanRate6);
        if (p == null) {
            toast("Enter the loan amount");
            return;
        }
        if (fee1 == null) fee1 = 4.0;
        if (r3 == null) r3 = 4.44;
        if (r6 == null) r6 = 6.26;
        double onePay = p * (1 + fee1 / 100.0);
        double m3 = emiOf(p, r3, 3);
        double m6 = emiOf(p, r6, 6);
        StringBuilder sb = new StringBuilder();
        sb.append("Your loan:  ").append(fmt2.format(p)).append("\n\n");
        sb.append("1 Month Installment:  ").append(fmt2.format(onePay))
          .append("  (single payment)\n");
        sb.append("   Total: ").append(fmt2.format(onePay))
          .append(" · Extra: ").append(fmt2.format(onePay - p)).append("\n\n");
        sb.append("3 Months Installment:  ").append(fmt2.format(m3)).append(" /month\n");
        sb.append("   Total: ").append(fmt2.format(m3 * 3))
          .append(" · Extra: ").append(fmt2.format(m3 * 3 - p)).append("\n\n");
        sb.append("6 Months Installment:  ").append(fmt2.format(m6)).append(" /month\n");
        sb.append("   Total: ").append(fmt2.format(m6 * 6))
          .append(" · Extra: ").append(fmt2.format(m6 * 6 - p));
        tvPlanResult.setText(sb.toString());
    }

    private void calculateLoan() {
        Double p = parse(etAmount);
        Double rate = parse(etRate);
        Double yrs = parse(etYears);
        Double mos = parse(etMonths);
        if (p == null || rate == null) {
            toast("Enter loan amount and interest rate");
            return;
        }
        int months = (int) Math.round((yrs == null ? 0 : yrs) * 12 + (mos == null ? 0 : mos));
        if (months <= 0) {
            toast("Enter the loan period in years or months");
            return;
        }
        double totalInterest, totalPay, monthly;
        StringBuilder sb = new StringBuilder();
        if (spLoanType.getSelectedItemPosition() == 0) {
            double r = rate / 1200.0;
            double emi;
            if (r == 0) {
                emi = p / months;
            } else {
                double f = Math.pow(1 + r, months);
                emi = p * r * f / (f - 1);
            }
            monthly = emi;
            totalPay = emi * months;
            totalInterest = totalPay - p;
            sb.append("Monthly EMI:  ").append(fmt2.format(emi)).append("\n");
        } else {
            double yearsDec = months / 12.0;
            totalInterest = p * rate * yearsDec / 100.0;
            totalPay = p + totalInterest;
            monthly = totalPay / months;
            sb.append("Monthly payment:  ").append(fmt2.format(monthly)).append("\n");
        }
        sb.append("Total interest:  ").append(fmt2.format(totalInterest)).append("\n");
        sb.append("Total payable:  ").append(fmt2.format(totalPay)).append("\n");
        sb.append("Period:  ").append(months).append(" months");
        tvLoanResult.setText(sb.toString());

        lastPrincipal = p;
        lastRateAnnual = rate;
        lastMonths = months;
        lastMonthlyPay = monthly;
        lastTotalInterest = totalInterest;
        lastIsEmi = spLoanType.getSelectedItemPosition() == 0;
        hasCalc = true;
    }

    // ---------- Amortization schedule ----------

    private TextView tvSchedule;

    private View buildScheduleView() {
        LinearLayout col = column();
        LinearLayout c = card();
        Button back = actionButton("←  Back to calculator");
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(0); }
        });
        c.addView(back);
        tvSchedule = resultText();
        tvSchedule.setTypeface(Typeface.MONOSPACE);
        tvSchedule.setTextSize(12.5f);
        c.addView(tvSchedule);
        col.addView(c);
        return scrollWrap(col);
    }

    private void showSchedule() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%-5s %11s %11s %11s %12s%n",
            "Mo", "Payment", "Principal", "Interest", "Balance"));
        double balance = lastPrincipal;
        double r = lastRateAnnual / 1200.0;
        for (int m = 1; m <= lastMonths; m++) {
            double interestPart, principalPart, pay;
            if (lastIsEmi) {
                interestPart = balance * r;
                pay = lastMonthlyPay;
                principalPart = pay - interestPart;
                if (m == lastMonths || principalPart > balance) {
                    principalPart = balance;
                    pay = principalPart + interestPart;
                }
            } else {
                interestPart = lastTotalInterest / lastMonths;
                pay = lastMonthlyPay;
                principalPart = pay - interestPart;
                if (m == lastMonths || principalPart > balance) principalPart = balance;
            }
            balance -= principalPart;
            if (balance < 0.005) balance = 0;
            sb.append(String.format(Locale.US, "%-5d %11s %11s %11s %12s%n",
                m, fmt2.format(pay), fmt2.format(principalPart),
                fmt2.format(interestPart), fmt2.format(balance)));
        }
        tvSchedule.setText(sb.toString());
        loanView.setVisibility(View.GONE);
        percentView.setVisibility(View.GONE);
        currencyView.setVisibility(View.GONE);
        goldView.setVisibility(View.GONE);
        scheduleView.setVisibility(View.VISIBLE);
    }

    // ---------- Percent tab ----------

    private View buildPercentTab() {
        LinearLayout col = column();

        LinearLayout c1 = card();
        c1.addView(cardTitle("What is X% of Y?"));
        c1.addView(label("Percent (%)"));
        etP1Percent = input("e.g. 15");
        c1.addView(etP1Percent);
        c1.addView(label("Of amount"));
        etP1Of = input("e.g. 2000");
        c1.addView(etP1Of);
        Button b1 = actionButton("Calculate");
        b1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Double pc = parse(etP1Percent), of = parse(etP1Of);
                if (pc == null || of == null) { toast("Enter both values"); return; }
                double ans = pc / 100.0 * of;
                tvP1Result.setText(fmt2.format(pc) + "% of " + fmt2.format(of)
                    + " = " + fmt2.format(ans));
            }
        });
        c1.addView(b1);
        tvP1Result = resultText();
        c1.addView(tvP1Result);
        col.addView(c1);

        LinearLayout c2 = card();
        c2.addView(cardTitle("X is what % of Y?"));
        c2.addView(label("Value (X)"));
        etP2Value = input("e.g. 45");
        c2.addView(etP2Value);
        c2.addView(label("Total (Y)"));
        etP2Total = input("e.g. 300");
        c2.addView(etP2Total);
        Button b2 = actionButton("Calculate");
        b2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Double x = parse(etP2Value), y = parse(etP2Total);
                if (x == null || y == null || y == 0) { toast("Enter both values (total cannot be 0)"); return; }
                double ans = x / y * 100.0;
                tvP2Result.setText(fmt2.format(x) + " is " + fmt2.format(ans)
                    + "% of " + fmt2.format(y));
            }
        });
        c2.addView(b2);
        tvP2Result = resultText();
        c2.addView(tvP2Result);
        col.addView(c2);

        LinearLayout c3 = card();
        c3.addView(cardTitle("Percentage increase / decrease"));
        c3.addView(label("From"));
        etP3From = input("e.g. 800");
        c3.addView(etP3From);
        c3.addView(label("To"));
        etP3To = input("e.g. 1000");
        c3.addView(etP3To);
        Button b3 = actionButton("Calculate");
        b3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Double a = parse(etP3From), b = parse(etP3To);
                if (a == null || b == null || a == 0) { toast("Enter both values (from cannot be 0)"); return; }
                double ch = (b - a) / a * 100.0;
                String word = ch > 0 ? "increase" : (ch < 0 ? "decrease" : "change");
                tvP3Result.setText(fmt2.format(a) + " → " + fmt2.format(b) + " is a "
                    + fmt2.format(Math.abs(ch)) + "% " + word
                    + "\nDifference: " + fmt2.format(b - a));
            }
        });
        c3.addView(b3);
        tvP3Result = resultText();
        c3.addView(tvP3Result);
        col.addView(c3);

        return scrollWrap(col);
    }

    // ---------- Currency tab ----------

    private View buildCurrencyTab() {
        LinearLayout col = column();

        LinearLayout c1 = card();
        c1.addView(cardTitle("Live Currency Converter"));
        TextView srcCur = resultText();
        srcCur.setTextSize(13);
        srcCur.setTextColor(MUTED);
        srcCur.setPadding(0, dp(2), 0, 0);
        srcCur.setText("Source: open.er-api.com (ExchangeRate API — USD base, refreshed daily)");
        c1.addView(srcCur);
        c1.addView(label("Amount"));
        etCurAmount = input("e.g. 100");
        etCurAmount.setText("1");
        c1.addView(etCurAmount);

        c1.addView(label("From"));
        spFrom = currencySpinner(0); // AED default
        c1.addView(spFrom);
        c1.addView(label("To"));
        spTo = currencySpinner(1); // INR default
        c1.addView(spTo);

        Button swap = actionButton("⇅  Swap currencies");
        swap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int a = spFrom.getSelectedItemPosition();
                spFrom.setSelection(spTo.getSelectedItemPosition());
                spTo.setSelection(a);
                convertCurrency();
            }
        });
        c1.addView(swap);

        Button conv = actionButton("Convert");
        conv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { convertCurrency(); }
        });
        c1.addView(conv);

        Button refresh = actionButton("↻  Refresh live rates");
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tvCurUpdated.setText("Fetching latest rates…");
                fetchRates();
            }
        });
        c1.addView(refresh);

        tvCurResult = resultText();
        tvCurResult.setTextSize(20);
        tvCurResult.setTypeface(Typeface.DEFAULT_BOLD);
        c1.addView(tvCurResult);
        tvCurRate = resultText();
        tvCurRate.setPadding(0, dp(4), 0, 0);
        c1.addView(tvCurRate);
        tvCurUpdated = resultText();
        tvCurUpdated.setTextSize(13);
        tvCurUpdated.setTextColor(MUTED);
        c1.addView(tvCurUpdated);
        col.addView(c1);
        return scrollWrap(col);
    }

    private Spinner currencySpinner(int defaultIdx) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item, CURRENCIES);
        s.setAdapter(ad);
        s.setSelection(defaultIdx);
        return s;
    }

    private void convertCurrency() {
        if (ratesJson == null) {
            tvCurResult.setText("");
            tvCurRate.setText("Rates not loaded yet — check your internet and tap Refresh.");
            return;
        }
        Double amt = parse(etCurAmount);
        if (amt == null) {
            toast("Enter an amount");
            return;
        }
        String from = CURRENCIES[spFrom.getSelectedItemPosition()];
        String to = CURRENCIES[spTo.getSelectedItemPosition()];
        try {
            JSONObject r = ratesJson.getJSONObject("rates");
            double rf = r.getDouble(from);
            double rt = r.getDouble(to);
            double converted = amt / rf * rt;
            tvCurResult.setText(fmt2.format(amt) + " " + from + " = "
                + fmt2.format(converted) + " " + to);
            tvCurRate.setText("1 " + from + " = " + fmtRate.format(rt / rf) + " " + to
                + "\n1 " + to + " = " + fmtRate.format(rf / rt) + " " + from);
        } catch (Exception ex) {
            tvCurRate.setText("Could not read rates for " + from + " / " + to);
        }
    }

    private void loadCachedRates() {
        try {
            SharedPreferences sp = getSharedPreferences("fincalc", MODE_PRIVATE);
            String cached = sp.getString("rates", null);
            String when = sp.getString("rates_when", null);
            if (cached != null) {
                ratesJson = new JSONObject(cached);
                tvCurUpdated.setText("Showing saved rates from " + when
                    + " — refreshing…");
                convertCurrency();
            }
        } catch (Exception ignored) {
        }
    }

    private void fetchRates() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    URL url = new URL("https://open.er-api.com/v6/latest/USD");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    final JSONObject json = new JSONObject(sb.toString());
                    if (!"success".equals(json.optString("result"))) throw new Exception("bad result");
                    final String updated = json.optString("time_last_update_utc", "");
                    SharedPreferences sp = getSharedPreferences("fincalc", MODE_PRIVATE);
                    sp.edit().putString("rates", sb.toString())
                        .putString("rates_when", updated).apply();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            ratesJson = json;
                            ratesLive = true;
                            tvCurUpdated.setText("Live rates · Updated: " + updated
                                + " · Source: open.er-api.com");
                            convertCurrency();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (ratesJson != null) {
                                tvCurUpdated.setText("Couldn't refresh — showing last saved rates (offline).");
                            } else {
                                tvCurUpdated.setText("Couldn't fetch live rates. Check your internet and tap Refresh.");
                            }
                        }
                    });
                }
            }
        }).start();
    }
        // ---------- Gold tab ----------

    private static final double GRAMS_PER_OZ = 31.1034768;
    private static final double GRAMS_PER_TOLA = 11.6638;
    private static final String[] KARATS = {"24K", "22K", "21K", "18K"};
    private static final double[] KARAT_FRAC = {1.0, 22.0 / 24, 21.0 / 24, 18.0 / 24};

    private View buildGoldTab() {
        LinearLayout col = column();

        LinearLayout c1 = card();
        c1.addView(cardTitle("Live Gold Rate"));
        TextView srcGold = resultText();
        srcGold.setTextSize(13);
        srcGold.setTextColor(MUTED);
        srcGold.setPadding(0, dp(2), 0, 0);
        srcGold.setText("Source: gold-api.com (live spot price, USD per troy ounce)");
        c1.addView(srcGold);
        c1.addView(label("Show prices in"));
        spGoldCurrency = currencySpinner(0); // AED default
        spGoldCurrency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                renderGold();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        c1.addView(spGoldCurrency);

        tvGoldPrices = resultText();
        tvGoldPrices.setTextSize(17);
        c1.addView(tvGoldPrices);

        Button refresh = actionButton("↻  Refresh gold price");
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tvGoldUpdated.setText("Fetching latest gold price…");
                fetchGold();
                if (ratesJson == null) fetchRates();
            }
        });
        c1.addView(refresh);

        tvGoldUpdated = resultText();
        tvGoldUpdated.setTextSize(13);
        tvGoldUpdated.setTextColor(MUTED);
        c1.addView(tvGoldUpdated);
        col.addView(c1);

        LinearLayout c2 = card();
        c2.addView(cardTitle("Gold Value Calculator"));
        c2.addView(label("Weight (grams)"));
        etGoldGrams = input("e.g. 8");
        c2.addView(etGoldGrams);
        c2.addView(label("Karat"));
        spGoldKarat = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item, KARATS);
        spGoldKarat.setAdapter(ad);
        spGoldKarat.setSelection(1); // 22K default (common for jewellery)
        c2.addView(spGoldKarat);
        Button calc = actionButton("Calculate value");
        calc.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { renderGold(); }
        });
        c2.addView(calc);
        tvGoldResult = resultText();
        tvGoldResult.setTextSize(18);
        tvGoldResult.setTypeface(Typeface.DEFAULT_BOLD);
        c2.addView(tvGoldResult);
        col.addView(c2);

        return scrollWrap(col);
    }

    private double goldRateForCurrency() {
        // Returns the USD->selected-currency multiplier (1.0 if USD or rates missing)
        String cur = CURRENCIES[spGoldCurrency.getSelectedItemPosition()];
        if ("USD".equals(cur) || ratesJson == null) return 1.0;
        try {
            return ratesJson.getJSONObject("rates").getDouble(cur);
        } catch (Exception e) {
            return 1.0;
        }
    }

    private void renderGold() {
        if (goldUsdPerOz <= 0) {
            tvGoldPrices.setText("Gold price not loaded yet — check your internet and tap Refresh.");
            return;
        }
        String cur = CURRENCIES[spGoldCurrency.getSelectedItemPosition()];
        double fx = goldRateForCurrency();
        if (!"USD".equals(cur) && ratesJson == null) {
            cur = "USD";
            fx = 1.0;
            tvGoldPrices.setText("(Currency rates still loading — showing USD)\n");
        } else {
            tvGoldPrices.setText("");
        }
        double perOz = goldUsdPerOz * fx;
        double perGram24 = perOz / GRAMS_PER_OZ;
        StringBuilder sb = new StringBuilder();
        sb.append("Per ounce:  ").append(fmt2.format(perOz)).append(" ").append(cur).append("\n");
        for (int i = 0; i < KARATS.length; i++) {
            sb.append(KARATS[i]).append(" per gram:  ")
              .append(fmt2.format(perGram24 * KARAT_FRAC[i])).append(" ").append(cur).append("\n");
        }
        sb.append("24K per tola (11.66 g):  ")
          .append(fmt2.format(perGram24 * GRAMS_PER_TOLA)).append(" ").append(cur);
        tvGoldPrices.append(sb.toString());

        Double grams = parse(etGoldGrams);
        if (grams != null && grams > 0) {
            int k = spGoldKarat.getSelectedItemPosition();
            double val = grams * perGram24 * KARAT_FRAC[k];
            tvGoldResult.setText(fmt2.format(grams) + " g of " + KARATS[k] + " gold ≈ "
                + fmt2.format(val) + " " + cur);
        } else {
            tvGoldResult.setText("");
        }
    }

    private void loadCachedGold() {
        try {
            SharedPreferences sp = getSharedPreferences("fincalc", MODE_PRIVATE);
            double p = Double.parseDouble(sp.getString("gold_usd", "-1"));
            String when = sp.getString("gold_when", "");
            if (p > 0) {
                goldUsdPerOz = p;
                tvGoldUpdated.setText("Showing saved gold price from " + when + " — refreshing…");
                renderGold();
            }
        } catch (Exception ignored) {
        }
    }

    private void fetchGold() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    URL url = new URL("https://api.gold-api.com/price/XAU");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    final JSONObject json = new JSONObject(sb.toString());
                    final double price = json.getDouble("price");
                    final String updated = json.optString("updatedAtReadable", "");
                    SharedPreferences sp = getSharedPreferences("fincalc", MODE_PRIVATE);
                    sp.edit().putString("gold_usd", String.valueOf(price))
                        .putString("gold_when", updated).apply();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            goldUsdPerOz = price;
                            tvGoldUpdated.setText("Live · Spot gold (USD/oz): "
                                + fmt2.format(price) + " · Updated " + updated
                                + " · Source: gold-api.com");
                            renderGold();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (goldUsdPerOz > 0) {
                                tvGoldUpdated.setText("Couldn't refresh — showing last saved gold price (offline).");
                            } else {
                                tvGoldUpdated.setText("Couldn't fetch the gold price. Check your internet and tap Refresh.");
                            }
                        }
                    });
                }
            }
        }).start();
}
}
