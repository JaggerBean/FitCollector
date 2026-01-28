# StepCraft React Dashboard

React + Vite dashboard for managing StepCraft servers (privacy, rewards, push notifications).

## Local development

1. Install dependencies: npm install
2. Create a .env file (optional) and set VITE_BACKEND_URL and VITE_GOOGLE_CLIENT_ID.
3. Run the dev server: npm run dev

## Docker (recommended for VPS)

Build and run the container:

- Build with a custom backend URL:
  docker build --build-arg VITE_BACKEND_URL=https://api.stepcraft.org -t stepcraft-react .

- Run:
  docker run -p 8080:80 stepcraft-react

## Docker Compose

1. Copy .env.example to .env and set VITE_BACKEND_URL if needed.
2. Start:
   docker compose up -d --build

The app will be available on http://localhost:8080.
