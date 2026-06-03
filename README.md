# Hello Spring Boot Service

Multi-module Java service communication demo for the AWS EKS CI/CD PoC.

Modules:

- `hello-contracts`: shared gRPC protobuf stubs and Dubbo Java interface.
- `hello-web`: REST Web service and consumer of gRPC/Dubbo providers.
- `hello-grpc-provider`: gRPC provider on port `9090`.
- `hello-dubbo-provider`: Dubbo 3 Triple provider on port `50051`.

## Local Run

```bash
mvn clean package
java -jar hello-web/target/hello-web-*.jar
curl http://localhost:8080/hello
```

Expected response:

```text
hello from spring boot web on eks
```

Run the gRPC provider locally:

```bash
java -jar hello-grpc-provider/target/hello-grpc-provider-*.jar
```

If `grpcurl` is installed, test it locally with:

```bash
grpcurl -plaintext \
  -d '{"name":"EKS"}' \
  localhost:9090 \
  hello.v1.HelloRpc/SayHello
```

Expected response:

```json
{
  "message": "hello EKS from grpc on eks"
}
```

Run the Dubbo provider locally:

```bash
java -jar hello-dubbo-provider/target/hello-dubbo-provider-*.jar
```

Then run the web service with provider endpoints:

```bash
HELLO_GRPC_TARGET=localhost:9090 \
HELLO_DUBBO_URL=tri://localhost:50051 \
java -jar hello-web/target/hello-web-*.jar
```

Test aggregate communication:

```bash
curl http://localhost:8080/hello/aggregate?name=EKS
```

## Container Build with Podman

```bash
mvn clean package
podman build -f hello-web/Dockerfile -t hello-springboot:web-local .
podman build -f hello-grpc-provider/Dockerfile -t hello-springboot:grpc-local .
podman build -f hello-dubbo-provider/Dockerfile -t hello-springboot:dubbo-local .
```

For local container testing:

```bash
podman run --rm -p 9090:9090 hello-springboot:grpc-local
podman run --rm -p 50051:50051 hello-springboot:dubbo-local
podman run --rm -p 8080:8080 \
  -e HELLO_GRPC_TARGET=host.containers.internal:9090 \
  -e HELLO_DUBBO_URL=tri://host.containers.internal:50051 \
  hello-springboot:web-local
```

## GitHub Actions

The workflow in `.github/workflows/ci.yml` does the following:

Pull request to `master`:

- run Maven tests;
- package the Spring Boot jar;
- build the three container images locally.

Push to `master` or manual dispatch:

- assume AWS role through GitHub OIDC;
- login to Amazon ECR;
- build and push images `web-<sha>`, `grpc-<sha>`, and `dubbo-<sha>` with Podman;
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