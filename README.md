# rLogin

**Autenticación premium automática + login para no-premium.** Para Paper,
Velocity (proxy) y Folia. Java 21→26, Minecraft 1.21→26.x.

> 🇬🇧 English: rLogin is a Minecraft auth plugin (Paper/Velocity/Folia) with
> automatic premium (Java-original) login and password login for cracked
> accounts. Full English messages are built in — set `general.language: en`
> in `config.yml`.

## ¿Qué hace distinto a rLogin?

La promesa es simple: **las cuentas premium entran a la partida sin
escribir nada**, y **las cuentas no-premium inician sesión con
`/login`/`/register`**, como en nLogin Premium — pero:

- Detección premium **criptográficamente verificada** vía Modern Forwarding
  de Velocity (no solo un chequeo de la API de Mojang que se puede falsear).
- Sesión **"recuérdame"** por IP: si un no-premium ya se logueó, no vuelve a
  pedirle contraseña al reconectar en poco tiempo.
- Al cambiar de servidor dentro de la misma red (mismo proxy), **no vuelve a
  pedir login**, ni a premium ni a no-premium, durante toda la conexión.
- 2FA (TOTP), anti fuerza bruta y protección de nombres premium, **todo
  opcional y configurable**.
- Migración desde AuthMe (nLogin/JPremium en camino, ver [Roadmap](#roadmap-fase-2)).
- Soporte Bedrock vía Floodgate.
- Pensado desde el primer día para Folia (schedulers regionales, sin usar el
  scheduler clásico de Bukkit en ningún punto sensible).

## Cómo funciona el auto-login premium

Cuando hay **Velocity** delante, rLogin usa la misma técnica que plugins de
referencia como FastLogin: el proxy corre en `online-mode: false`, pero en
`PreLoginEvent` consulta si el nombre que se conecta es una cuenta premium
real (caché local + [API de Mojang](https://api.mojang.com)) y, si lo es,
fuerza el handshake cifrado con Mojang **solo para esa conexión**
(`forceOnlineMode()`). El cliente se autentica solo, sin contraseña. Si no
es premium, la conexión pasa en modo offline y rLogin le pide `/login` en
el backend.

En **Paper/Folia standalone** (sin proxy) la detección es aún más simple:
se compara el UUID real del jugador con el que generaría el propio servidor
en modo offline para ese mismo nombre. Si no coinciden, es que alguien ya
lo verificó contra Mojang (el propio servidor en `online-mode: true`, o un
proxy con Modern Forwarding) — sin repetir ninguna llamada de red.

> **Límite conocido:** mezclar "premium automático" + "cracked con
> contraseña" **en un único Paper/Folia sin proxy delante** no es posible de
> forma robusta multiversión sin inyección de bajo nivel en Netty (lo que
> hacen algunos plugins de forma no oficial y frágil). Si quieres esa
> combinación, la vía soportada y recomendada es **Velocity + backends
> Paper/Folia**. Un servidor standalone puede ser 100% premium
> (`online-mode: true`) o 100% cracked (`online-mode: false`, login con
> contraseña normal) sin ningún problema.

## Requisitos

- Java 21 o superior (compilado con `--release 21`, corre igual en versiones
  posteriores).
- Paper 1.21+ (o Folia 1.21+) para el backend.
- Velocity 3.x (opcional, solo si quieres auto-login premium en red).

## Instalación

1. Descarga `rLogin-Paper.jar` (y `rLogin-Velocity.jar` si usas proxy).
2. Colócalo en `plugins/` de cada Paper/Folia (y en `plugins/` de Velocity
   si aplica) y arranca una vez para que genere `config.yml`.
3. Si usas Velocity:
   - `velocity.toml`: `player-info-forwarding-mode = "modern"`.
   - En cada backend Paper/Folia: `online-mode: false` en `server.properties`,
     y `config/paper-global.yml` → `proxies.velocity.enabled: true` +
     `online-mode: true` (para que confíe en el forwarding). Copia el mismo
     `forwarding.secret` de Velocity a cada backend.
4. Ajusta `plugins/rLogin/config.yml` a tu gusto (ver más abajo) y
   `/rlogin reload`.

## Comandos

| Comando | Alias | Descripción |
|---|---|---|
| `/login <contraseña> [código-2fa]` | `/l`, `/rlogin login` | Inicia sesión |
| `/register <contraseña> <repite>` | `/reg`, `/rlogin register` | Crea tu cuenta |
| `/changepassword <actual> <nueva>` | `/rlogin changepassword` | Cambia tu contraseña |
| `/logout` | `/rlogin logout` | Cierra tu sesión |
| `/2fa enable\|disable\|confirm <código>` | `/rlogin 2fa` | Gestiona el 2FA |
| `/premium` | `/rlogin premium` | Consulta tu estado premium |

Administración (`rlogin.admin`):

| Comando | Descripción |
|---|---|
| `/rlogin reload` | Recarga `config.yml` y los mensajes |
| `/rlogin unregister <jugador>` | Elimina una cuenta |
| `/rlogin forcelogin <jugador>` | Autentica a un jugador a la fuerza |
| `/rlogin migrate <authme\|nlogin\|jpremium> <ruta-o-jdbc>` | Importa cuentas |
| `/rlogin info <jugador>` | Info de una cuenta |

## Permisos

- `rlogin.admin` (`op` por defecto) — comandos de administración.
- `rlogin.bypass` (`false` por defecto) — omite el requisito de login (NPCs, bots de prueba...).

## Base de datos

`database.type: sqlite` (por defecto, cero configuración) o `mysql`
(recomendado si varios backends Paper/Folia deben compartir las mismas
cuentas — ver `config.yml`).

## Migración desde otros plugins

```
/rlogin migrate authme plugins/AuthMe/authme.db
/rlogin migrate authme "jdbc:mysql://usuario:contraseña@host:3306/authme"
```

Reconoce contraseñas en bcrypt y en el SHA256 por defecto de AuthMe (se
re-hashean a bcrypt automáticamente en el primer login correcto tras
migrar). Otros algoritmos de AuthMe (MD5, WHIRLPOOL...) se importan pero
necesitan que el jugador vuelva a registrarse.

## Roadmap (Fase 2)

Este es un proyecto vivo. Pendiente para próximas versiones:

- Importadores reales de nLogin y JPremium/LoginSecurity (ahora mismo lanzan
  un error explícito en vez de fingir que funcionan — se agradecen PRs).
- Generación de código QR para el setup de 2FA (de momento se da la clave +
  URI `otpauth://` en texto).
- Captcha en pantalla tras varios intentos fallidos.
- Métricas bStats.
- Modo standalone híbrido (premium + cracked sin proxy) si aparece una forma
  fiable y multiversión de hacerlo.

## Compilar desde el código fuente

```
./gradlew build
```

Genera `rlogin-paper/build/libs/rLogin-Paper.jar` y
`rlogin-velocity/build/libs/rLogin-Velocity.jar`. Necesita acceso de red al
repositorio de PaperMC (`repo.papermc.io`), donde viven los artefactos de
Paper API y Velocity API.

## Estructura del proyecto

```
rlogin-api/       Interfaces públicas (Storage, Importer) — SPI para addons de terceros
rlogin-common/    Lógica pura Java: config, i18n, BD, seguridad, auth, migración
rlogin-velocity/  Plugin de proxy: decide online/offline-mode por conexión
rlogin-paper/     Plugin de backend: cuentas, comandos, congelación, Folia
```

## Licencia

MIT — ver [LICENSE](LICENSE). Autor: [raimondarias](https://github.com/raimondarias).
