"""Generator for the Benefits Administration UAT Excel test script.
Extended per phase — append new (id, feature, role, steps, expected) tuples to CASES
as milestones are delivered, then re-run this script to regenerate the workbook."""
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.utils import get_column_letter

OUT = "/Users/elshad/Millers HCM/PRD/HCM_11_Benefits_Administration_Multi_Tenant_PRD/Benefits_UAT_Test_Script.xlsx"
FONT = "Arial"
navy = "1F3864"; blue = "2E5496"; band = "F2F5FB"; grey = "808080"; white = "FFFFFF"
thin = Side(style="thin", color="BFBFBF"); border = Border(thin, thin, thin, thin)

# Delivered so far: Phase A — M373 categories, M374 providers, M375 plan tiers + eligibility.
# Phases B–F append here as they ship.
CASES = [
 ("SET-01","Setup","HR Admin","Sign in as HR Admin at http://localhost:5180. Open the top-nav 'Benefits' page (Benefits administration). You should see tabs: Categories, Providers, Plans catalog, Enrolments, My benefits.","The Benefits page opens with the five tabs visible."),
 # ---------- Phase A.1: categories (M373) ----------
 ("CAT-01","Categories (M373)","HR Admin","Benefits → Categories tab.","Eight seeded categories are listed in order: Health Insurance, Life Insurance, Pension / Retirement, Meal Allowance, Transport Allowance, Housing Allowance, Mobile / Communication, Wellness Program."),
 ("CAT-02","Categories (M373)","HR Admin","On the Categories list, read the Taxable and Provider columns for Health vs Meal.","Health = Exempt + Provider Required; Meal = Taxable + no provider. (Insured plans are tax-exempt; cash-like allowances are taxable.)"),
 ("CAT-03","Categories (M373)","HR Admin","Click 'New category…'. Code=GYM, Name=Gym Membership, tick Taxable off, Provider off, Order=9. Save.","The new category GYM appears at the end of the list, Active."),
 ("CAT-04","Categories (M373)","HR Admin","Click 'New category…' again and re-use the code HEALTH. Save.","Rejected — category code must be unique; an error is shown and no row is created."),
 ("CAT-05","Categories (M373)","HR Admin","Open the GYM category, untick Active, Save. Then toggle the 'Active only' switch.","GYM shows Inactive (not deleted); with 'Active only' on it is hidden from the list."),
 # ---------- Phase A.2: providers (M374) ----------
 ("PRV-01","Providers (M374)","HR Admin","Benefits → Providers tab → 'New provider…'. Code=PASHA-INS, Name=Pasha Insurance OJSC, Type=Insurer, Contact email=account@pasha.az, Contract number=C-2026-01, Contract window = today → +1 year. Save.","The provider PASHA-INS appears in the table with type Insurer, the contract number and the contract window."),
 ("PRV-02","Providers (M374)","HR Admin","Create a provider with a Contract window whose end date is BEFORE its start date. Save.","Rejected — contract end must be on/after start; an error is shown."),
 ("PRV-03","Providers (M374)","HR Admin","Create a second provider (Code=CAPITAL-PF, Name=Kapital Pension Fund, Type=Pension fund). Save.","The pension-fund provider appears."),
 ("PRV-04","Providers (M374)","HR Admin","Enter an invalid email (e.g. 'abc') in Contact email and try to save.","Rejected — the email format is validated."),
 # ---------- Phase A.3: plans + tiers + eligibility (M375) ----------
 ("PLN-01","Plans (M375)","HR Admin","Benefits → Plans catalog → 'New plan…'. Code=HEALTH-FAM-26, Name=Family Health 2026, Type=Health, Category=HEALTH — Health Insurance, Plan year=2026, Provider (from master)=Pasha Insurance, Employer/mo=120, Employee/mo=30, Effective window=this year. Save.","The plan appears in the table showing the Category tag (Health Insurance), Year 2026 and Provider = Pasha Insurance OJSC (the master name, not free text)."),
 ("PLN-02","Plans (M375)","HR Admin","On the HEALTH-FAM-26 row click 'Tiers…'. In the Coverage tiers section click 'Add tier' three times and set: Employee only Er=100/Ee=0; Employee + spouse Er=150/Ee=40; Family Er=220/Ee=90 (add a Cover/sum-insured on one). Save.","The three tiers save; reopening 'Tiers…' shows them with the right employer/employee amounts."),
 ("PLN-03","Plans (M375)","HR Admin","Reopen 'Tiers…' and add a second tier with the SAME tier code as an existing one (e.g. two 'Family'). Save.","Rejected — each coverage tier code must be unique (message shown)."),
 ("PLN-04","Plans (M375)","HR Admin","In 'Tiers…' → Eligibility rules, click 'Add rule'. Employment type=FULL_TIME, Min service=3 (mo), Note='FT after probation'. Save.","The eligibility rule saves; reopening shows it. (No rules = open to all; this rule limits the plan to full-time staff with 3+ months service — enforced at enrolment in Phase B.)"),
 ("PLN-05","Plans (M375)","HR Admin","Open the plan editor for HEALTH-FAM-26 and confirm Category and Plan year are populated; change Provider (from master) to blank and instead type a free-text Provider. Save, then look at the Provider column.","With a master provider it shows the master name; cleared, it falls back to the free-text provider name."),
 # ---------- Phase A permissions ----------
 ("SEC-A1","Permissions (Phase A)","HR Specialist","Sign in as HR Specialist. Open Benefits → Categories / Providers / Plans.","You can view the lists, but 'New…' / Save / the Tiers… editor are read-only or unavailable (write is HR Admin / Benefits Manager only)."),
 ("SEC-A2","Permissions (Phase A)","Employee","Sign in as a plain Employee and open the Benefits page.","Only the 'My benefits' tab is available — the admin tabs (Categories, Providers, Plans, Enrolments) are hidden."),
]

wb = Workbook()

# Instructions
ws = wb.active; ws.title = "Instructions"; ws.sheet_view.showGridLines = False
ws.column_dimensions["A"].width = 3; ws.column_dimensions["B"].width = 26; ws.column_dimensions["C"].width = 100
def put(r,l,v,bold=True):
    b=ws.cell(r,2,l); b.font=Font(name=FONT,bold=bold,size=11,color=navy if bold else "000000"); b.alignment=Alignment(vertical="top",wrap_text=True)
    c=ws.cell(r,3,v); c.font=Font(name=FONT,size=11); c.alignment=Alignment(vertical="top",wrap_text=True)
ws.cell(1,2,"Benefits Administration — User Acceptance Test (UAT) Script").font=Font(name=FONT,bold=True,size=16,color=navy)
ws.cell(2,2,"HCM_11 Benefits Administration  •  front-end (browser) testing  •  grows as each phase ships").font=Font(name=FONT,size=11,color=grey)
for r,l,v in [
 (4,"How to use","Open the 'Test Cases' sheet. Do exactly what the Steps say (which tab, which button, what to type), compare to the Expected Result, and pick Pass / Fail / Blocked / Not Run in the Result column. Add anything unusual under Tester Notes. Fill the Sign-off sheet at the end."),
 (6,"Application URL","http://localhost:5180 — sign in first. Benefits features are under the top-nav 'Benefits' page."),
 (7,"Delivered so far","Phase A COMPLETE — Benefit Categories (M373), Provider/Vendor master (M374), and Plan enrichment: coverage tiers + eligibility rules + category & plan-year (M375). Enrolment, contributions→payroll, open enrollment, claims, dashboards and letters arrive in later phases."),
 (9,"Logins you will need","HR Admin (full benefits access), HR Specialist (read-only), Employee (self-service). If you only have an admin login, mark the role-restriction rows Blocked with a note."),
 (11,"Result values","Pass = worked as expected.  Fail = did not match (add a note).  Blocked = could not run (missing login/data).  Not Run = skipped."),
 (12,"Good to know","Categories drive tax treatment + whether a plan needs a provider. Plans link to a category, a plan year and a provider from the master. Coverage tiers set the employer/employee split per coverage level; eligibility rules decide who may enrol (enforced from Phase B)."),
]:
    put(r,l,v)
ws.row_dimensions[4].height=45; ws.row_dimensions[7].height=60; ws.row_dimensions[12].height=45

# Test Cases
tc = wb.create_sheet("Test Cases"); tc.sheet_view.showGridLines=False
headers=["Test ID","Feature Area","Login As","Test Steps (what to click / type)","Expected Result","Result","Tester Notes"]
widths=[12,22,18,62,58,12,30]
for i,(h,w) in enumerate(zip(headers,widths),1):
    c=tc.cell(1,i,h); c.font=Font(name=FONT,bold=True,color=white,size=11); c.fill=PatternFill("solid",fgColor=navy)
    c.alignment=Alignment(horizontal="center",vertical="center",wrap_text=True); c.border=border
    tc.column_dimensions[get_column_letter(i)].width=w
tc.freeze_panes="A2"; tc.row_dimensions[1].height=26
for idx,(tid,feat,role,steps,exp) in enumerate(CASES):
    r=idx+2; banded=(idx%2==1)
    for col,v in enumerate([tid,feat,role,steps,exp,"",""],1):
        c=tc.cell(r,col,v)
        c.font=Font(name=FONT,size=11,bold=(col==1),color=(navy if col==1 else "000000"))
        c.alignment=Alignment(vertical="top",wrap_text=True,horizontal=("center" if col in(1,6) else "left"))
        c.border=border
        if banded and col!=6: c.fill=PatternFill("solid",fgColor=band)
    tc.cell(r,6).fill=PatternFill("solid",fgColor="FFF7E6")
    longest=max(len(steps),len(exp)); tc.row_dimensions[r].height=max(30,min(160,(longest//60+1)*15+12))
dv=DataValidation(type="list",formula1='"Pass,Fail,Blocked,Not Run"',allow_blank=True)
dv.add(f"F2:F{len(CASES)+1}"); tc.add_data_validation(dv)

# Sign-off
so=wb.create_sheet("Sign-off"); so.sheet_view.showGridLines=False
for col,w in zip("ABCDE",[3,36,16,22,18]): so.column_dimensions[col].width=w
so.cell(1,2,"Benefits UAT — Sign-off").font=Font(name=FONT,bold=True,size=15,color=navy)
for i,h in enumerate(["Feature Area","Result","Tester","Date"],2):
    c=so.cell(3,i,h); c.font=Font(name=FONT,bold=True,color=white); c.fill=PatternFill("solid",fgColor=blue); c.border=border
areas=["Categories (CAT)","Providers (PRV)","Plans + Tiers + Eligibility (PLN)","Permissions (SEC)"]
dv2=DataValidation(type="list",formula1='"Pass,Fail,Blocked,Not Run"',allow_blank=True); so.add_data_validation(dv2)
for j,a in enumerate(areas):
    r=4+j; so.cell(r,2,a).font=Font(name=FONT,size=11)
    for col in range(2,6): so.cell(r,col).border=border
    so.cell(r,3).fill=PatternFill("solid",fgColor="FFF7E6"); dv2.add(so.cell(r,3))
fr=4+len(areas)+2
so.cell(fr,2,"Phase A decision (SHIP / DON'T SHIP):").font=Font(name=FONT,bold=True,size=12,color=navy)
so.cell(fr,3).fill=PatternFill("solid",fgColor="FFF7E6"); so.cell(fr,3).border=border
so.cell(fr+2,2,"Tester name:").font=Font(name=FONT,size=11); so.cell(fr+3,2,"Date:").font=Font(name=FONT,size=11)

wb.save(OUT)
print("WROTE",OUT,"cases:",len(CASES))
