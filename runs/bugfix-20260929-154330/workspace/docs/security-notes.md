# Security notes: destination URL validation

`UrlValidator` normalizes every destination URL, and `HostClassifier` decides whether its host is public.

Rejected hosts:

- `localhost`, `*.localhost` and `*.local`.
- Literal IPs in loopback, link-local, unspecified and private ranges, including integer, hex and shortened IPv4
  forms such as `2130706433` and `0x7f000001`.
- **Absolute names** with a trailing dot, such as `localhost.` and `printer.local.`. They resolve exactly like
  their relative form, so they are classified after the trailing dot is removed. Public absolute names such as
  `example.com.` are still accepted.

Names are never resolved, so DNS rebinding is out of scope.
