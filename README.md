# Hello Spring Boot Service

Minimal Spring Boot service for the AWS EKS CI/CD PoC.

## Local Run

```bash
mvn clean package
java -jar target/hello-springboot-service-*.jar
curl http://localhost:8080/hello
```

Expected response:

```text
hello from spring boot on eks
```

## Container Build with Podman

```bash
mvn clean package
podman build -t hello-springboot:local .
podman run --rm -p 8080:8080 hello-springboot:local
```

## GitHub Actions

The workflow in `.github/workflows/ci.yml` does the following:

Pull request to `master`:

- run Maven tests;
- package the Spring Boot jar;
- build the Docker image locally.

Push to `master` or manual dispatch:

- assume AWS role through GitHub OIDC;
- login to Amazon ECR;
- build and push image `hello-springboot:master-<sha>` with Podman;
- checkout the GitOps repository;
- update `gitops/apps/hello-springboot/values-dev.yaml`;
- open a GitOps pull request so protected `master` can be reviewed and merged.

## Required GitHub Configuration

Repository secrets:

- `AWS_APP_CI_ROLE_ARN`: output from `ai-platform-infra/infra/live/dev/iam-github`.
- `GITOPS_REPO_TOKEN`: fine-grained GitHub token or GitHub App token with write access to infra repository contents and pull requests.

Repository variables:

- `AWS_REGION`: for example `us-east-1`.
- `GITOPS_REPO`: for example `your-user-or-org/ai-platform-infra`.

The branch protection step may not be enforced on a private repository unless
you use GitHub Team or Enterprise. For this PoC, AWS access is still restricted
by the OIDC trust policy to the configured repository and `master` branch.