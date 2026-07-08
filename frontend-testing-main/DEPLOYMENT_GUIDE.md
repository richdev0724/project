# 🚀 Deployment Guide for Market Mono

This guide covers multiple hosting options for your full-stack React + Node.js application with WebSocket support.

## 📋 Prerequisites

Before deploying, ensure you have:
- [ ] GitHub repository with your code
- [ ] Environment variables documented
- [ ] Database setup (if using external database)
- [ ] Domain name (optional)

## 🛤️ Option 1: Railway (Recommended)

**Best for**: Quick deployment, WebSocket support, integrated database

### Steps:
1. **Sign up**: Go to [Railway.app](https://railway.app) and connect your GitHub
2. **Create Project**: Click "New Project" → "Deploy from GitHub repo"
3. **Configure Services**:
   - Railway will auto-detect your frontend and backend
   - Set environment variables in the Railway dashboard
   - The app will auto-deploy on every Git push

### Environment Variables:
```bash
NODE_ENV=production
PORT=8080
# Add your specific environment variables
```

### Pricing:
- **Hobby Plan**: $5/month + usage
- **Pro Plan**: $20/month + usage

---

## 🎨 Option 2: Render

**Best for**: Free tier, great performance, separate service management

### Backend Deployment:
1. **Sign up**: Go to [Render.com](https://render.com)
2. **New Web Service**: Connect your GitHub repo
3. **Configure**:
   - **Root Directory**: `backend`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Plan**: Free or Starter ($7/month)

### Frontend Deployment:
1. **New Static Site**: Connect same GitHub repo
2. **Configure**:
   - **Build Command**: `npm install && npm run build`
   - **Publish Directory**: `dist`
   - **Plan**: Free

### Environment Variables:
Set in Render dashboard for backend service.

### Pricing:
- **Free Tier**: Available (with limitations)
- **Starter**: $7/month per service

---

## 🌊 Option 3: DigitalOcean App Platform

**Best for**: Production apps, scalability, integrated services

### Steps:
1. **Sign up**: Go to [DigitalOcean](https://digitalocean.com)
2. **Create App**: Apps → Create App → GitHub
3. **Configure**: Use the provided `.do/app.yaml` configuration
4. **Deploy**: App will build and deploy automatically

### Pricing:
- **Basic**: $12/month
- **Professional**: $25/month

---

## 🔧 Option 4: Self-Hosted VPS

**Best for**: Full control, cost-effective for high traffic

### Recommended Providers:
- **DigitalOcean Droplets**: $6/month
- **Linode**: $5/month  
- **Vultr**: $6/month

### Setup Process:
1. **Create VPS**: Ubuntu 22.04 LTS
2. **Install Dependencies**:
   ```bash
   # Update system
   sudo apt update && sudo apt upgrade -y
   
   # Install Node.js 18
   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
   sudo apt-get install -y nodejs
   
   # Install PM2
   sudo npm install -g pm2
   
   # Install Nginx
   sudo apt install nginx -y
   ```

3. **Deploy Application**:
   ```bash
   # Clone repository
   git clone https://github.com/your-username/your-repo.git
   cd your-repo
   
   # Install backend dependencies
   cd backend
   npm install
   
   # Install frontend dependencies and build
   cd ../
   npm install
   npm run build
   
   # Start backend with PM2
   cd backend
   pm2 start src/index.js --name "market-mono-backend"
   pm2 startup
   pm2 save
   ```

4. **Configure Nginx**:
   ```nginx
   # /etc/nginx/sites-available/market-mono
   server {
       listen 80;
       server_name your-domain.com;
       
       # Frontend
       location / {
           root /path/to/your-repo/dist;
           try_files $uri $uri/ /index.html;
       }
       
       # Backend API
       location /api {
           proxy_pass http://localhost:8080;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection 'upgrade';
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_cache_bypass $http_upgrade;
       }
       
       # WebSocket support
       location /socket.io/ {
           proxy_pass http://localhost:8080;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```

5. **Enable Site**:
   ```bash
   sudo ln -s /etc/nginx/sites-available/market-mono /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl restart nginx
   ```

6. **SSL Certificate** (Optional):
   ```bash
   sudo apt install certbot python3-certbot-nginx -y
   sudo certbot --nginx -d your-domain.com
   ```

---

## 🔒 Environment Variables

Create a `.env` file in your backend directory:

```bash
# Server Configuration
NODE_ENV=production
PORT=8080

# Database (if using)
DATABASE_URL=your_database_url

# API Keys
POLYGON_API_KEY=your_polygon_api_key
CLICKHOUSE_URL=your_clickhouse_url
CLICKHOUSE_PASSWORD=your_clickhouse_password

# CORS Origins (update for your domain)
ALLOWED_ORIGINS=https://your-domain.com,https://www.your-domain.com
```

---

## 📊 Comparison Table

| Platform | Cost | WebSocket | Database | Auto-Deploy | SSL | Difficulty |
|----------|------|-----------|----------|-------------|-----|------------|
| Railway | $5/mo | ✅ | ✅ | ✅ | ✅ | Easy |
| Render | Free/7$ | ✅ | ✅ | ✅ | ✅ | Easy |
| DO Apps | $12/mo | ✅ | ✅ | ✅ | ✅ | Easy |
| VPS | $6/mo | ✅ | Manual | Manual | Manual | Hard |

---

## 🚀 Quick Start (Railway - Recommended)

1. **Push to GitHub**: Ensure your code is in a GitHub repository
2. **Sign up**: Go to [Railway.app](https://railway.app)
3. **Deploy**: 
   - Click "New Project"
   - Select "Deploy from GitHub repo"
   - Choose your repository
   - Railway will auto-detect and deploy both services
4. **Configure**: Add environment variables in Railway dashboard
5. **Done**: Your app will be live with auto-deployments!

---

## 🔍 Troubleshooting

### Common Issues:

1. **WebSocket Connection Failed**:
   - Ensure your hosting platform supports WebSockets
   - Check CORS configuration in backend
   - Verify Socket.IO client/server versions match

2. **Build Failures**:
   - Check Node.js version compatibility
   - Ensure all dependencies are in package.json
   - Verify build commands are correct

3. **Environment Variables**:
   - Double-check variable names
   - Ensure sensitive data is not committed to Git
   - Verify variables are set in hosting dashboard

### Need Help?
- Check hosting provider documentation
- Review application logs
- Test locally first with production build

---

**Recommendation**: Start with Railway for the easiest deployment experience, then consider migrating to a VPS as your app scales.




