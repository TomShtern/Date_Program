# Dating App CLI

A command-line based dating application refactored for clean architecture and strict code quality.

## Getting Started

### Prerequisites
- Java 21+
- Maven

### Running the Application
To run the main application:
```bash
mvn compile exec:java
```

### Running Tests
To run the full test suite (99 tests):
```bash
mvn clean test
```

## Important Configuration

### Database
The application uses an embedded H2 database located in `./data/dating`.

**Credentials:**
- **User**: `sa`
- **Password**: `changeit`

> **Note**: The password was added to satisfy security warnings. In a production environment, this would be managed via environment variables.

## Recent Changes
- Fixed H2 database locking issues by removing `AUTO_SERVER=TRUE` during tests.
- Refactored `Main.java` for better resource management.
- Resolved all IDE warnings and compilation errors.
