# Payroll — User Acceptance Test Script (click-by-click)

**For the tester.** Follow each test in order. Do exactly what the **Steps** say
(which menu, which button), then compare what you see against **Expected result**.
Write **PASS** or **FAIL** in the Result column and add a note if anything looks wrong.

- Open the app: **http://localhost:5180** and sign in.
- The payroll menus are under the top navigation bar item **“Payroll”**.
- You will sign in as different users during testing. The roles used:
  **HR Admin**, **Payroll Specialist**, **HR Specialist**, **Employee**.
  (If you only have one admin login, do the “Employee” tests with an employee login and
  skip the role-restriction checks, noting that in the Result column.)

> Tip: after saving anything, a small green message appears bottom-right on success,
> or a red message on error.

---

## Part A — One-time test setup (HR Admin)

Do this once so later tests have data to work with.

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| A1 | Sign in as **HR Admin**. Make sure there is a test employee (e.g. “Test One”) who is **Active**, has a **monthly base salary of 2000**, a **bank account with an IBAN**, and belongs to a department. | The employee opens without errors and shows a salary of 2000. | | |
| A2 | Confirm the employee has an **approved timesheet** for the test month (e.g. July 2026). | Timesheet status shows Approved. | | |

---

## Part B — Salary Components (Payroll → Components)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| B1 | Payroll → **Components**. Click **Create Component**. Fill: Code `MEAL`, Name `Meal Allowance`, Kind `EARNING`, Calculation `FIXED_AMOUNT`, Amount `50`, **Is Taxable = ON**. Save. | New row **MEAL** appears in the table with a green **EARNING** tag. | | |
| B2 | Create another: Code `TRANSPORT`, Kind `EARNING`, Amount `150`, **Is Taxable = OFF**. Notice the **Contribution Exempt** switch. | While Is Taxable is ON the Contribution-Exempt switch is disabled; with Is Taxable OFF it becomes editable. Row **TRANSPORT** is saved. | | |
| B3 | In the list, find a **statutory** component (e.g. **Income Tax**, shown with a lock/statutory marker). Try to delete or edit it. | The system refuses — statutory components cannot be deleted. There are **5** statutory rows (Income Tax, DSMF Employee, MMI Employee, Unemployment Employee, DSMF Employer). | | |
| B4 | Assign a component to the employee: open the **employee’s profile → Compensation tab → Salary Components → Add assignment**. Choose **MEAL**, amount `50`, effective from the 1st of the test month. Save. Repeat for **TRANSPORT** (150). | Both assignments appear in the employee’s Salary Components list with no “to” date (still active). | | |
| B5 | Back on Payroll → **Components**, try to delete **MEAL** (now in use). | Deletion is refused because the component is assigned to an employee. | | |
| B6 | **Sign out, sign in as HR Specialist.** Open Payroll → Components and the employee’s Salary Components. | You can see the component **names** but every **amount / percentage is hidden/blank** (salary confidentiality). Sign back in as HR Admin afterwards. | | |

---

## Part C — Payroll Holds & Pre-flight (Payroll → Runs)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| C1 | Payroll → **Runs**. Create a new **Regular** run for the test month (Run Type = **REGULAR**). Open the run. | The run opens; the runs list shows a **Run Type** column with a **REGULAR** tag. | | |
| C2 | In the run, open **Pre-flight** (button/section “Run pre-flight”). | A checklist appears grouped into: **No Compensation, No Timesheet, On Hold, Pending Advances, Retroactive Salary Change**, with a total count. | | |
| C3 | In the run’s **Holds** section click **Add hold**, pick the test employee, reason “Contract dispute”, save. Then **Calculate** the run. | The employee is listed under Holds; after Calculate the employee is **not** in the results. | | |
| C4 | In Holds click **Release** for that employee, then **Calculate** again. | The employee is now **included** in the results. | | |

---

## Part D — Off-cycle run

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| D1 | Payroll → Runs → create run, set Run Type = **OFF_CYCLE**, but leave **Employees** empty. Try to save. | Save is refused — an off-cycle run must name at least one employee. | | |
| D2 | Create the off-cycle run again with **Description** filled and **one employee** selected. Calculate. | The run is created and calculated for only that employee; it appears in the list with an **OFF_CYCLE** tag, alongside the regular run for the same month. | | |

---

## Part E — Salary Advances (Payroll → Advances)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| E1 | **Sign in as the Employee.** Go to **My Workspace → Payroll tab → My Advances → Request Advance**. Enter amount `1000`, a reason, submit. | The request is created and shows status **PENDING** (1000 is exactly 50% of the 2000 salary, so it’s allowed). | | |
| E2 | As the Employee, request another advance `1001`. | Rejected — over the 50% limit. | | |
| E3 | With a PENDING advance already present, try to request a second one. | Rejected — only one advance at a time. | | |
| E4 | **Sign in as Payroll Specialist.** Payroll → Advances → open the pending advance → **Approve**. | **Not allowed** for Payroll Specialist (approval is HR-Admin only). | | |
| E5 | **Sign in as HR Admin.** Payroll → Advances → open the advance → **Approve Advance**: approved amount `800`, repayment month = the **next** month. Confirm. | Status becomes **APPROVED**; approved amount 800; repayment month set. | | |
| E6 | Payroll → Runs → create/open the **next month’s** Regular run → Calculate → open the employee’s payslip line. | Net pay is reduced by **800**; the advance now shows status **DEDUCTED**. | | |
| E7 | Create a fresh advance, approve it, then (before it’s deducted) **Cancel** it. Then open an already-**DEDUCTED** advance and try to cancel. | First cancel works (status CANCELLED). Cancelling a deducted advance is refused. | | |
| E8 | **As the Employee**, try to cancel an advance that belongs to **another** employee (if you can reach it). | Refused — you can only cancel your own advance. | | |

---

## Part F — Payroll Loans (Payroll → Loans)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| F1 | Sign in as HR Admin. Payroll → **Loans** → **Create Loan**: employee = test employee, principal `3000`, monthly installment `500`, start month = test month. Save. | Loan created, status **ACTIVE**, and the screen shows the term as **6** months, outstanding **3000**. | | |
| F2 | Calculate the test month’s Regular run and open the employee’s payslip. | An installment of **500** is deducted; the loan’s outstanding drops to **2500**. | | |
| F3 | On the same run, click **Calculate again** (recalculate). | The installment is still **500** (not 1000) and outstanding is still **2500** — recalculating does **not** double-deduct. | | |
| F4 | Calculate the **following** month’s run. | Another **500** is deducted (the loan keeps deducting month after month). | | |
| F5 | **As Payroll Specialist**, open a loan → **Write Off**. | **Not allowed** (write-off is HR-Admin only). | | |
| F6 | **As HR Admin**, open a loan → **Write Off Loan**, enter a reason, confirm. | Status becomes **WRITTEN_OFF**. | | |

---

## Part G — Cost Centers & GL Journal (in a run)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| G1 | Open the employee’s profile → Compensation → **Cost Center Allocations → Update Allocations**. Add one row `CC-ENG` `70`% only. Save. | Refused — allocations must total **100%** (a running total is shown). | | |
| G2 | Change to two rows `CC-ENG` `60` and `CC-PROD` `40`, effective the test month. Save. | Saved (totals 100%). | | |
| G3 | Take a run through approval so its status is **Approved** (or Paid). Open the run’s **GL Journal** tab → **Generate journal**. | A journal appears with debit and credit lines. **Total Debit equals Total Credit** and a green “balanced” indicator is shown. | | |
| G4 | Try **Generate journal** on a run that is still **Draft** (not approved). | Refused — the journal can only be generated once the run is Approved/Paid. | | |
| G5 | On the GL Journal tab click **Export CSV**. | A CSV file downloads with the journal lines. | | |

---

## Part H — PDF Payslips (in a run)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| H1 | On a run that is **not yet Paid**, open the **Payslips** tab and click **Generate all payslips**. | Refused/blocked — payslips are only generated after the run is **Paid**. | | |
| H2 | Take the run to **Paid**. On the **Payslips** tab click **Generate all payslips**. | A count of generated payslips is shown; each employee row gets a **Download** link. | | |
| H3 | Click **Download** on the employee’s payslip. | A PDF opens showing earnings (base + MEAL + TRANSPORT), statutory deductions, any advance/loan deductions, **Net pay**, and the bank **IBAN masked** to the last 4 digits (e.g. ****6789). | | |
| H4 | On the Payslips tab click **Send by email** (if present) and confirm. | A result like “sent N, failed M” appears; employees without an email are skipped, not an error. (If MailHog is available, the email with the PDF appears there.) | | |
| H5 | **Sign in as the Employee** → My Workspace → Payroll → **My Payslips** → **Download** the month’s payslip. | The employee can download **their own** payslip only; there is no way to see another employee’s. | | |

---

## Part I — Year-End & Tax Certificate (Payroll → Year-End)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| I1 | Sign in as HR Admin. Payroll → **Year-End**. Pick the year (e.g. 2026). Click **Generate Summaries**. | A success message; a table of annual summaries appears (only employees with paid runs that year). | | |
| I2 | Click **Generate All Certificates**, then **Load Certificates**. | The certificates table shows, per employee: Annual Gross, Exempt, Taxable, Tax Withheld, status **GENERATED**. The **national ID is masked** (last 4 only) in this list. | | |
| I3 | Click **Download certificate** on a row. | A PDF opens showing the employee name, full tax IDs (VÖEN), annual gross, exempt, taxable, tax withheld, and the period 01-Jan…31-Dec. | | |
| I4 | **As the Employee** → My Workspace → Payroll → **My Tax Certificate**, choose the year, **Download**. | The employee downloads **their own** certificate only. | | |

---

## Part J — Reports & Control Board

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| J1 | Payroll → **Control Board**. | Cards show: Current Run Status, Headcount, Total Gross, Total Net, Total Tax, MoM Gross Variance %, Outstanding Loan Balance, Pending Advance Requests, plus Quick Actions. Numbers look sensible. | | |
| J2 | Payroll → **Variance Report**. Choose a **prior** run and a **current** run (both regular/paid). | A table shows each employee’s prior vs current gross, the change and %, and coloured flags (e.g. New Employee, Salary Change). A summary row shows totals and a High-Variance count. | | |
| J3 | Open the **Reports** area and view **Period Summary**, **Employer Cost**, **Loan & Advance Status**, and **Bank Reconciliation** for a run. | Each report loads with data: period totals + per-employee lines; employer contribution costs; a list of active loans and pending advances; a bank total vs payroll net total with a matched/mismatch indicator. | | |

---

## Part K — Permissions spot-check (quick)

| # | Steps | Expected result | Result | Notes |
|---|-------|-----------------|--------|-------|
| K1 | Sign in as **Employee**. Try to open Payroll → Components / Advances (admin list) / Loans directly. | Employee cannot reach the HR payroll admin screens; only **My Workspace → Payroll** self-service is available. | | |
| K2 | Sign in as **HR Specialist**. Open any payroll list with amounts. | Can view lists but **salary amounts are hidden**; cannot create/edit components, approve advances, or write off loans. | | |
| K3 | Sign in as **Payroll Specialist**. Try **Approve Advance** and **Write Off Loan**. | Both are **blocked** (HR-Admin only). Payroll Specialist can still create runs, components, advances, loans. | | |

---

## Overall sign-off

| Area | PASS / FAIL | Tester | Date |
|------|-------------|--------|------|
| B — Components | | | |
| C — Holds & Pre-flight | | | |
| D — Off-cycle run | | | |
| E — Advances | | | |
| F — Loans | | | |
| G — Cost centers & GL | | | |
| H — Payslips | | | |
| I — Year-End & Certificate | | | |
| J — Reports & Control Board | | | |
| K — Permissions | | | |

**Final decision: SHIP ☐  /  DON’T SHIP ☐**   Tester: ______________  Date: __________

Blocking issues found (if any):
_______________________________________________________________________________
_______________________________________________________________________________
