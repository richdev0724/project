# Google Authentication Setup Guide

## Prerequisites
- A Google account
- Access to [Google Cloud Console](https://console.cloud.google.com)

## Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Click on the project dropdown in the top navbar
3. Click **"New Project"**
4. Enter a project name (e.g., "Market Mono")
5. Click **"Create"**

## Step 2: Enable Google+ API

1. In your project, go to **"APIs & Services"** > **"Library"**
2. Search for **"Google+ API"** or **"Google Identity"**
3. Click on it and press **"Enable"**

## Step 3: Create OAuth 2.0 Credentials

1. Go to **"APIs & Services"** > **"Credentials"**
2. Click **"Create Credentials"** > **"OAuth Client ID"**
3. If prompted, configure the OAuth consent screen:
   - Choose **"External"** user type
   - Fill in the app name: **"Market Mono"**
   - Add your email address
   - Click **"Save and Continue"**
4. Back on the credentials page, select **"Web application"** as the application type
5. Give it a name (e.g., "Market Mono Web Client")

## Step 4: Configure Authorized Origins and Redirect URIs

### For Development:
- **Authorized JavaScript origins:**
  - `http://localhost:5173`
  - `http://localhost:3000` (if you use port 3000)

- **Authorized redirect URIs:**
  - `http://localhost:5173`
  - `http://localhost:5173/sign-in`
  - `http://localhost:5173/sign-up`

### For Production:
- **Authorized JavaScript origins:**
  - `https://yourdomain.com`
  - `https://www.yourdomain.com`

- **Authorized redirect URIs:**
  - `https://yourdomain.com`
  - `https://yourdomain.com/sign-in`
  - `https://yourdomain.com/sign-up`

## Step 5: Get Your Client ID

1. After creating the credentials, you'll see a modal with:
   - **Client ID** (looks like: `123456789-abcdefghijklmnop.apps.googleusercontent.com`)
   - **Client Secret** (you don't need this for frontend-only OAuth)

2. Copy the **Client ID**

## Step 6: Update Your .env File

1. Open the `.env` file in your project root
2. Replace `your-google-client-id-here.apps.googleusercontent.com` with your actual Client ID:

```bash
VITE_GOOGLE_CLIENT_ID=123456789-abcdefghijklmnop.apps.googleusercontent.com
```

## Step 7: Restart Your Development Server

1. Stop your current dev server (Ctrl+C)
2. Restart it:
```bash
npm run dev
```

## Testing

1. Navigate to `http://localhost:5173/sign-in`
2. Click the **"Continue with Google"** button
3. You should see the Google OAuth consent screen
4. Select your Google account
5. You'll be redirected back to your app and logged in

## Troubleshooting

### Error: "redirect_uri_mismatch"
- Make sure `http://localhost:5173` is added to both:
  - Authorized JavaScript origins
  - Authorized redirect URIs
- The URL must match exactly (including protocol and port)

### Error: "invalid_client"
- Double-check your Client ID in the `.env` file
- Make sure there are no extra spaces or quotes

### Error: "access_denied"
- The user declined to authorize the app
- Make sure your OAuth consent screen is properly configured

### Button doesn't work
- Check the browser console for errors
- Make sure the `.env` file is loaded (restart dev server)
- Verify the `VITE_GOOGLE_CLIENT_ID` environment variable is set correctly

## Security Notes

- **Never commit your `.env` file to Git** - it's already in `.gitignore`
- For production, use environment variables from your hosting provider
- Keep your Client Secret private (though we don't use it in frontend-only OAuth)
- Regularly review authorized users in Google Cloud Console

## Additional Resources

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [@react-oauth/google Documentation](https://www.npmjs.com/package/@react-oauth/google)

