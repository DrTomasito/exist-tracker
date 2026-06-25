# Work Distraction Tracker — Browser Extension Setup

This small extension runs in Microsoft Edge (or Chrome) on your work computer.
It privately counts the time your *active tab* spends on sites you choose
(Engadget, Gizmodo, Guardian, YouTube, etc.) and once a day sends the totals
to your Exist account, where your phone's Exist Tracker app picks them up and
folds YouTube minutes into your daily YouTube total.

Nothing is installed on the computer — browser extensions just load inside
Edge, which you said you're allowed to use.

## What you need first
- The same **Exist access token** your phone app uses (from exist.io/account/apps/).
  The extension writes directly to Exist with it.

## Install in Edge (or Chrome)
1. Unzip the `WorkTrackerExtension` folder somewhere on your work computer
   (e.g. Documents). Keep the folder — the extension loads from it.
2. In Edge, go to the address bar and type:  `edge://extensions`
   (In Chrome it's `chrome://extensions`.)
3. Turn on **Developer mode** (toggle, usually top-right or bottom-left).
4. Click **Load unpacked**.
5. Select the `WorkTrackerExtension` folder. The extension appears.
6. Pin it: click the puzzle-piece icon in the toolbar, then the pin next to
   "Work Distraction Tracker," so its icon is always visible.

## Configure it
1. Click the extension icon. A small panel opens showing today's totals.
2. Click **Settings** to expand the options.
3. **Reporting method:** leave on **Exist (recommended)**.
4. **Distraction sites:** the domains to count, comma-separated. Default is
   `engadget.com, gizmodo.com, theguardian.com`. Add or remove as you like.
5. **YouTube domains:** leave as `youtube.com` (tracked separately so your
   phone can combine it with phone-YouTube into one daily total).
6. **Exist access token:** paste your token.
7. **Distractions attribute / Work-YouTube attribute:** leave the defaults
   (`work_distractions` and `work_youtube`) unless you changed them in the
   phone app's Step 7. **They must match the phone app's Step 7 settings.**
8. Click **Save settings**.
9. Click **Send today's totals now** once to confirm it works — the status
   line should show "Last sent: …" with no error.

## How it counts
- Only the tab you're actively looking at counts, and only while you're not
  idle (no input for 60s pauses counting).
- It rolls over at midnight automatically and sends the finished day's totals.
- It also re-sends every 30 minutes as a safety net, so the latest number is
  always in Exist by the time your phone posts at 11:50 PM.

## If your work network blocks Exist
Some workplaces block outbound calls to exist.io. If the status line keeps
showing an error:
1. Make a free account at **jsonbin.io**.
2. Create a bin (it gives you a **Bin ID**); copy your **Master Key** from
   the API Keys page.
3. In the extension Settings, switch **Reporting method** to **jsonbin**, and
   paste the Bin ID and Master Key. Save.
4. In the phone app's **Step 7**, switch the relay to **jsonbin** and paste
   the same Bin ID, plus your jsonbin **Access Key** (a read key is fine for
   the phone). Now the extension writes to jsonbin and the phone reads it.

## Honest limitations
- This is the most fragile part of the whole setup. A work-managed computer
  may block "Developer mode" extensions or outbound API calls entirely; if so,
  none of this will work and it's your IT policy, not the extension.
- It only sees Edge/Chrome activity — not other browsers or apps.
- Time is active-tab only, so a distraction site sitting in a background tab
  doesn't count (which is what you asked for).
