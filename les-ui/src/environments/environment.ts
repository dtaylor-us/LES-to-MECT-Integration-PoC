export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081/api',
  // Admin credentials for the admin-only write operations (HTTP Basic Auth).
  // These match the LES_ADMIN_USERNAME / LES_ADMIN_PASSWORD env vars on the backend.
  // FOR LOCAL DEVELOPMENT ONLY – never use these defaults in production.
  adminUsername: 'admin',
  adminPassword: 'changeme',
};
