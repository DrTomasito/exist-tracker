# Exist Tracker — Build & Setup Guide (no coding needed)

This app runs quietly in the background, measures five things each day, and
posts them to your Exist.io account at 11:50 PM, then resets at 11:58 PM.

What it tracks (you can rename or change any of these inside the app):

| What                | How it measures it                       | Exist group |
|---------------------|------------------------------------------|-------------|
| Hospital Time       | Minutes connected to WiFi `NCHS-GUEST`   | Location    |
| Home Time           | Minutes connected to WiFi `Gilligan`     | Location    |
| Youtube Time        | Minutes the YouTube app is in foreground | Social      |
| Social Media Time   | Minutes in Instagram + Facebook combined | Social      |
| Time Driving        | Minutes Bluetooth `Handsfreelink` connected | Location |

You will do three things, in order:
- **PART A:** Set up an Exist developer app (5 min, on the Exist website)
- **PART B:** Build the APK using GitHub (free, in your browser)
- **PART C:** Install it on your phone and turn on permissions

---

## PART A — Set up your Exist app (gives the app permission to write data)

Writing data to Exist requires what's called an "OAuth2 app." This is a
one-time setup.

1. On a computer, go to **https://exist.io/account/apps/** and log in.
2. Click **Create a new app** (or "New OAuth2 client").
3. Fill it in like this:
   - **Name:** Exist Tracker (anything you like)
   - **Redirect URI:** `https://exist.io/account/apps/`  ← type exactly this
   - **Scopes:** tick **manual_read** and **manual_write**
     (these let the app create and write your custom attributes)
4. Save. Exist will show you a **Client ID** and a **Client Secret**.
   Keep this page open — you'll copy these two values into the app later.

That's all on the Exist side for now.

---

## PART B — Build the app into an installable file (APK), free, in your browser

You don't need to install anything. GitHub will build it for you in the cloud.

### B1. Make a free GitHub account
- Go to **https://github.com** and sign up (free).

### B2. Create a new repository
- Click the **+** (top right) → **New repository**.
- Name it `exist-tracker`.
- Choose **Public** (Public repos get free build minutes).
- Click **Create repository**.

### B3. Upload the project files
- On the new repo page, click **uploading an existing file**
  (the link in the "Quick setup" box), OR **Add file → Upload files**.
- Unzip `ExistTracker-project.zip` on your computer first.
- Drag **everything INSIDE the `ExistTracker` folder** into the upload box —
  that means `app`, `gradle`, `.github`, `build.gradle`, `settings.gradle`,
  `gradle.properties`. (Don't drag the outer `ExistTracker` folder itself;
  drag its contents so `app` and `.github` sit at the top level of the repo.)
- Tip: the `.github` folder is essential. If your computer hides it, in the
  upload box you can usually still drag it; if not, see the note at the bottom.
- Scroll down, click **Commit changes**.

### B4. Let GitHub build it
- Click the **Actions** tab at the top of your repo.
- You should see a workflow called **Build APK** running (yellow dot).
  If it asks you to enable workflows, click the green **enable** button.
- Wait ~3–5 minutes until it shows a green checkmark.
  (If it didn't start on its own: click **Build APK** on the left, then
  **Run workflow → Run workflow**.)

### B5. Download the APK
- Click the finished (green) run.
- Scroll to the bottom to the **Artifacts** section.
- Click **ExistTracker-app** to download a zip.
- Inside is the file **app-release.apk** — that's your app.

---

## PART C — Install and set up on your Android phone

### C1. Get the APK onto your phone
- Email the `app-release.apk` to yourself, or download it directly on the
  phone from GitHub, or transfer via USB/Google Drive.

### C2. Install it
- Tap the APK. Android will warn it's from an "unknown source."
- Tap **Settings → allow this source / install anyway**, then **Install**.
  (This is normal for apps not from the Play Store.)

### C3. Open "Exist Tracker" and follow the on-screen steps
The app screen is laid out as Steps 1–4:

**Step 1 — Connect your Exist account**

Two ways — pick whichever is easier:

*Easy way (paste tokens):* If your Exist app page already shows an Access
token and Refresh token, tap **Enter Client ID & Secret** and save those two,
then tap **Paste tokens directly**, paste both tokens, Save. Done — no browser
needed. (Client ID & Secret are still worth saving so the app can refresh the
token automatically when it expires once a year.)

*Browser way:*
- Tap **Enter Client ID & Secret**, paste the two values from PART A, Save.
- Tap **Connect to Exist (log in)**. Your browser opens Exist.
- Tap **Allow**.
- Your browser address bar will now show something ending in
  `…?code=XXXXXXXX`. Copy just the **XXXXXXXX** part (the code).
- Switch back to the app, paste the code, tap **Connect**.
- The top of the screen should now say **✓ Connected to Exist.**

**Step 2 — Grant permissions** (tap each button, then Allow)
- **Location → choose "Allow all the time."** ← REQUIRED for WiFi name to work.
  Android will not tell you the WiFi name unless location is "all the time."
- **Background location**, **Notifications**, **Nearby devices/Bluetooth** —
  allow each.
- **Usage access** — this opens a settings list. Find **Exist Tracker** and
  turn its switch ON. (Needed to measure YouTube/Instagram/Facebook time.)
- **Disable battery optimization** — allow. This stops Android from killing
  the tracker overnight.

**Step 3 — What to track**
- The five trackers are pre-filled with your values. You can change any:
  - The first box of each is the **Exist attribute name**. You usually won't
    type this by hand — instead tap **Choose from Exist / create new…** under
    it. That opens a dropdown of your actual Exist attributes (so no typos),
    and the top option lets you **create a brand-new attribute**: you give it
    a name and pick which **category/group** it belongs to (Location, Social,
    etc.), and the app creates it in Exist right then. If an attribute is
    already filled by another service, the app offers to take ownership so it
    can write to it.
  - The second box is what it watches: a **WiFi name**, **Bluetooth device
    name**, or **app package(s)**.
  - For Social Media, package names are comma-separated. Defaults are
    `com.instagram.android,com.facebook.katana`. Add more if you like.
- The same **Choose / create** button appears for the optional metrics in
  Step 5 and the work-computer attributes in Step 7.

> Note on the dropdown: it lists attributes your connection is allowed to read.
> If you connected with **Paste tokens directly**, make sure the token you
> generated on Exist includes read access to the groups you care about,
> otherwise some attributes won't appear. The browser **Connect to Exist**
> login already requests the right permissions for you.

**Step 4 — Run**
- Tap **Start tracking.** A quiet notification appears showing live minute
  totals. That means it's working.
- Tap **Post to Exist now (test)** to push current values immediately and
  confirm everything connects. Check the status line at the top.

From then on it posts automatically at 11:50 PM and resets at 11:58 PM daily.

**Step 8 — Time together (with your wife)**
- This measures how long you and your wife are together each day, using a small
  private cloud backend plus the free OwnTracks app on her iPhone.
- Full setup is in the separate **DEPLOYMENT_GUIDE.md** (in the TogetherBackend
  folder). In short: deploy the backend free on Render, point her OwnTracks app
  and your app's Step 8 at it, and a nightly trigger posts the total to Exist.
- On the **Dashboard** you'll also see **Together awake** — the together total
  minus the time you were asleep (since you sleep similar hours). You can
  optionally post that to Exist too via the checkbox in Step 8.

**The Dashboard (home screen)**
- When you open the app you land on a dark **Dashboard** showing today at a
  glance: when you arrived at work and were last seen there, your time buckets,
  where you watched YouTube (work/home/out), time together, a chart of whether
  you're leaving work earlier over the weeks, and your typical departure time by
  weekday.
- Buttons at the bottom take you to **Activity timers** (stopwatches), **All
  trends & charts**, and **Settings & connections**.

**Activity counters & timers**
- Tap **⏱️ Activity timers** on the Dashboard to open the Counters tab.
- Two kinds of trackers live here:
  - **Timers** (stopwatches) — tap the circle to start timing, then Pause,
    Save (adds to today's total + the log), or Cancel. Good for "Charting",
    "Gaming", "Notes".
  - **Counters** (clickers) — tap to +1. Good for tallies like "Coffees" or
    "Cases". A −1 undo appears if you mistap.
- **＋ Timer** / **＋ Counter** create new ones. For each you set: a name, a
  color, whether to **also push the daily total to Exist** (pick or create an
  attribute) or keep it **internal-only**, and whether to **pin it to the home
  screen**.
- Reorder them with the ▲ ▼ arrows. Long-press a name to edit, pin, or delete.
- Running timers keep counting if you switch apps; a phone restart stops them.
  You can run several at once (it warns about overlap but allows it).
- Anything you chose to push posts its daily total to Exist each night and
  appears as a weekly chart in **All trends & charts**.

**Pinned counters on the Dashboard**
- Any tracker you pin shows in a **Quick counters** card on the home screen,
  just below the work summary. Tap its circle to act directly — +1 a counter,
  or start/stop a timer — without opening the Counters tab.

**Viewing your trends**
- Tap **📈 View Trends & Charts** at the bottom of the main screen.
- Top charts: Work (Hospital), YouTube, and Instagram + Facebook weekly
  work-week averages, each with a plain-language trend sentence.
- **Today's breakdown** shows how much of your YouTube and social time
  happened at Work vs Home vs Away (with percentages), a Home line split into
  awake vs asleep, and your phone screen-in-use time.
- More charts below track screen time, home-awake time, and the work/home
  splits over the weeks.
- Charts fill in over time; tapping "Post to Exist now" records today so you
  can watch them populate immediately.

**Step 5 — Extra metrics (optional)**
- Screen time, sleep, and the work/home splits are always shown in Trends.
- Tick a box next to any of them to ALSO post it to Exist, and set the
  attribute name in the box beneath. Leave unticked to keep it in-app only.

**Step 6 — Backup & restore (moving to a new phone)**
- Tap **Export settings to a file** → save `exist-tracker-backup.json`
  (e.g. to Google Drive or Downloads).
- New phone: install the app, tap **Import settings from a file**, pick that
  backup. Credentials, tokens, attribute names, toggles, and history are all
  restored and the app reloads. Keep the file private — it contains tokens.

**About sleep splitting (optional)**
- "Home awake vs asleep" needs **Health Connect** with a sleep source. Tap
  **Sleep access (Health Connect)** in Step 2 and allow. If your phone has no
  Health Connect or no sleep data, the app just shows total Home time and skips
  the split — nothing breaks.

---

## Good to know

- **Battery:** It samples once a minute and runs as a low-priority foreground
  service, so battery use is small. Keeping it exempt from battery
  optimization (Step 2) is what keeps it reliable overnight.
- **WiFi name shows blank?** That's the location permission. It MUST be
  "Allow all the time," and your phone's Location toggle (in quick settings)
  must be on.
- **Bluetooth name:** It matches your device name loosely, so "Handsfreelink"
  matches your car. If driving time stays 0, open the app, check the exact
  Bluetooth name your phone shows for the car, and put that in the box.
- **Changing attributes later:** Just edit the name boxes in Step 3 anytime.
- **Rebuilding after a change to code:** push the changed file to GitHub and
  the Actions tab rebuilds a fresh APK automatically.

### If `.github` won't upload from your computer
Some computers hide folders starting with a dot. Workaround: in your repo,
click **Add file → Create new file**, and in the name box type:
`.github/workflows/build.yml` (typing the slashes creates the folders), then
paste the contents of the build.yml file from the project, and commit.
