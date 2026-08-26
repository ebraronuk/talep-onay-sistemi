# Derleme asamasi
FROM node:22-alpine AS derleme

WORKDIR /derleme

# Once kilit dosyasi: kaynak degistiginde npm ci katmani onbellekten gelsin.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Sunum asamasi: statik dosyalar icin nginx yeterli, Node calisma zamanina gerek yok.
FROM nginx:1.27-alpine AS calisma

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=derleme /derleme/dist /usr/share/nginx/html

EXPOSE 80

# 127.0.0.1, localhost degil: nginx yalnizca IPv4 dinliyor ama busybox wget
# once ::1'i deniyor ve "connection refused" aliyor. Konteyner calisir halde
# oldugu halde saglik kontrolu kirmizi yaniyordu.
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=6 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
