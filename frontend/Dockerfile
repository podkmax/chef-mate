# ===== build =====
FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --prefer-offline --no-audit --silent
COPY . .
RUN npm run build

# ===== run =====
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# базовый nginx.conf не обязателен, дефолт подходит
EXPOSE 80
