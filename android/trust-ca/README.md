# Bundled HTTPS CA

Place your self-signed **CA certificate** here:

```
android/trust-ca/api_trust_ca.pem
```

Then rebuild the Android app. Gradle copies it into the APK and trusts it for:

- API requests (Retrofit / OkHttp)
- Image loading (Coil)
- Other HTTPS traffic (via `network_security_config`)

## File format

PEM file, for example:

```
-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----
```

Use the **CA certificate** that signed your API server cert (not necessarily the server cert itself, unless self-signed).

## Export examples

```bash
# mkcert local CA
cp "$(mkcert -CAROOT)/rootCA.pem" android/trust-ca/api_trust_ca.pem

# OpenSSL CA file you created
cp /path/to/your/ca.pem android/trust-ca/api_trust_ca.pem
```

## Notes

- This file is gitignored by default (`*.pem`). Each deployment can use its own CA.
- Until the file exists, HTTPS uses system CAs only (Let's Encrypt, etc.).
- Plain HTTP still works when the API URL is prefixed with `http://` in Settings.
