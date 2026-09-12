# Google Drive Storage Integration Setup Guide

Follow these step-by-step instructions to connect your personal Google Account (e.g., your 5 TB storage plan) to CloudBox Family.

---

## Step 1: Create a Project in Google Cloud Console

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Sign in with any of your Google Accounts.
3. Click the project dropdown in the top-left corner and click **New Project**.
4. Name the project (e.g., `CloudBox Family Storage`) and click **Create**.
5. Select the newly created project from the dropdown.

---

## Step 2: Enable Google Drive API

1. In the left sidebar, navigate to **APIs & Services** > **Library**.
2. Search for **Google Drive API**.
3. Click on **Google Drive API** and click **Enable**.

---

## Step 3: Configure the OAuth Consent Screen

1. In the left sidebar, navigate to **APIs & Services** > **OAuth consent screen**.
2. Select **External** (if using a standard personal account) and click **Create**.
3. **App Information**:
   - **App name**: `CloudBox Family`
   - **User support email**: Select your email.
   - **Developer contact information**: Enter your email.
4. Click **Save and Continue**.
5. **Scopes**:
   - Click **Add or Remove Scopes**.
   - In the filter manually paste: `https://www.googleapis.com/auth/drive.file`
   - Also select the standard `.../auth/userinfo.email` and `openid` scopes.
   - Click **Update** and then **Save and Continue**.
6. **Test Users**:
   - Since your app will be in "Testing" mode initially, Google restricts connections to explicitly listed test users.
   - Click **Add Users** and enter your personal **5 TB Google Account email address** (and any other family member emails who will connect admin storage).
   - Click **Save and Continue** and then **Back to Dashboard**.

---

## Step 4: Create OAuth 2.0 Credentials

1. In the left sidebar, navigate to **APIs & Services** > **Credentials**.
2. Click **Create Credentials** at the top and select **OAuth client ID**.
3. **Application type**: Select **Web application**.
4. **Name**: `CloudBox Backend Web Client`
5. **Authorized redirect URIs**:
   - Click **Add URI** and enter:
     `http://127.0.0.1:8000/admin/storage/google/callback`
   - *Note: If you run CloudBox on a different domain or IP in production, add that redirect URI here as well.*
6. Click **Create**.
7. A dialog will show your **Client ID** and **Client Secret**. Copy these values.

---

## Step 5: Configure Local Environment Variables

1. Open your local `.env` file (located in `d:\software\cloudbox_Backup\backend\.env`). If it doesn't exist, copy it from `.env.example`:
   ```powershell
   Copy-Item d:\software\cloudbox_Backup\backend\.env.example d:\software\cloudbox_Backup\backend\.env
   ```
2. Open `d:\software\cloudbox_Backup\backend\.env` in an editor and configure the following variables at the bottom:
   ```env
   GOOGLE_CLIENT_ID=your-copied-client-id
   GOOGLE_CLIENT_SECRET=your-copied-client-secret
   GOOGLE_OAUTH_REDIRECT_URI=http://127.0.0.1:8000/admin/storage/google/callback
   ```
3. Save the `.env` file.

---

## Step 6: Restart Backend Server

1. If your backend server is currently running, stop it (Ctrl+C).
2. Start it again to load the new environment variables:
   ```powershell
   cd d:\software\cloudbox_Backup
   python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000
   ```

---

## Step 7: Connect Google Drive from Admin Dashboard

1. Start your Admin Dashboard (if not already running):
   - Navigate to `/admin-ui/settings` (or open `http://localhost:5173/admin-ui/settings` in development).
2. Look at the **Storage** panel. You should see:
   - **Google Drive**: `Disconnected`
3. Click the **Connect Google Drive** button.
4. This opens Google's account selection page.
5. Because of the `prompt=select_account` parameter, Google will display the list of all signed-in Google accounts.
6. **Manually select your personal 5 TB Google account**.
7. Google may display a screen saying "Google hasn't verified this app" (since it is a self-hosted app). Click **Advanced** > **Go to CloudBox Family (unsafe)** to proceed.
8. Approve the requested permissions (access to view and manage Google Drive files that you open or create with this app).
9. You will be redirected back to the CloudBox Settings page.

---

## Step 8: Verify Status and Set Provider

1. On returning, the status panel will display:
   - **Google Drive**: `Connected`
   - **Connected account**: `your-5tb-account@gmail.com`
   - **Total storage**: (e.g. `5.00 TB`)
   - **Used storage**: (current usage on Drive)
   - **Available storage**: (calculated remaining space)
2. Under **Storage provider for new uploads**, select **Google Drive** from the dropdown.
3. Scroll down and click **Save settings** to persist.
4. **All set!** New user uploads will now write to the `CloudBox/users/user-[id]/` folder in your 5 TB Google Drive storage, while respecting their server-side admin-defined quotas.
