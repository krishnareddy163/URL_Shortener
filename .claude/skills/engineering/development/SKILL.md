---
version: 1.0.0
updated: 2026-09-29
changes:
  - "1.0.0 (2026-09-29): Initial version"
---

# Development Skill

**Purpose:** Implement the designed solution following project standards, with production-quality code.  
**Prerequisites:** Analysis outputs (acceptance criteria). Design outputs (API contract, data model) for non-trivial changes.  
**Outputs feed:** testing skill, observability skill.

---

## When to use this skill

Every task that involves writing or changing code.

---

## Inputs

- Acceptance criteria from analysis
- Design document (API, data model, component design)
- Existing codebase conventions (read before writing)

---

## Pre-implementation checklist

Before writing a line of production code:

- [ ] I have read the existing code in the files I am about to change
- [ ] I understand the naming conventions, package structure, and patterns in use
- [ ] I have the API contract and data model defined (or they are trivially simple)
- [ ] I know which acceptance criteria each piece of code must satisfy

---

## Implementation standards

### Code structure

- Follow the project's existing package and file structure exactly
- One class / one responsibility: if a class has two reasons to change, split it
- Keep methods short: if a method needs a comment to explain what a block does, extract the block
- Name things for what they are, not for how they are implemented

### Error handling

- Catch errors at the boundary where you can provide context, not deep inside helper code
- Never swallow exceptions silently (`catch (Exception e) {}` is never acceptable)
- Distinguish recoverable errors (bad input → 400) from unrecoverable ones (DB down → 500)
- Use the project's existing error/exception types; add a new type only when the existing ones
  genuinely do not fit
- Log every caught exception with enough context to diagnose it without a debugger

### Logging

- Log at the boundary of significant state transitions: request received, result returned,
  external call made, external call returned
- Include correlation IDs / request IDs in every log line (use MDC or equivalent)
- Log what happened, not what the code is about to do: "Link created: id=abc123" not "Creating link"
- Use structured logging (JSON or key=value pairs); never concatenate user input into log messages
- Log levels: ERROR for errors requiring investigation, WARN for degraded operation, INFO for normal
  business events, DEBUG for developer troubleshooting (disabled in production)
- Never log secrets, passwords, PII, or full request bodies containing sensitive fields

### Configuration

- Externalise everything that differs between environments: URLs, timeouts, feature flags, limits
- Provide sensible defaults so the service starts without all values set
- Document every configuration property with its type, default, and effect
- Never hardcode a URL, credential, or environment-specific value

### Security

- Validate all inputs at the boundary (format, length, allowed characters)
- Never trust client-supplied values for authorisation decisions without verification
- Never log, store, or return secrets in API responses
- Sanitise data before writing it to logs or HTML (prevent injection)
- Use the project's existing authentication and authorisation mechanisms

### Performance

- Don't add a database call inside a loop
- Use pagination for queries that could return unbounded result sets
- Set timeouts on all external calls (HTTP, DB, cache)
- Don't load the entire dataset into memory to process one item

### Dependency management

- Add only dependencies the code actually uses
- Prefer the project's existing libraries over new ones for the same purpose
- New third-party dependencies require a brief justification

---

## Implementation workflow

1. **Write the interface first** (method signature, return type, errors). Agree on the contract
   before writing the body.
2. **Implement the happy path** end-to-end, keeping the build green.
3. **Add validation and error handling** to each code path.
4. **Add logging** at significant state transitions.
5. **Run existing tests** — they must stay green at each step.
6. **Commit logically**: each commit should contain one coherent change with a clear message.

---

## Outputs

- Production code that satisfies all acceptance criteria
- All existing tests still green
- No new compiler warnings introduced
- No hardcoded environment-specific values
- Logging on all significant state transitions and error paths
