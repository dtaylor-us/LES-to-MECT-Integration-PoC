# Build Angular with production API base (/api so nginx can proxy)
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --legacy-peer-deps

COPY . .
# Build for production (apiUrl: /api)
RUN npm run build

# Serve with nginx and proxy /api to LES
FROM nginx:alpine
COPY --from=build /app/dist/les-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
