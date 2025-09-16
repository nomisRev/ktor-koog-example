# koog-sample

This project demonstrates the use of [Koog](https://github.com/koog-ai/koog) to create an AI agent with access to external tools.
It's built on top of [Ktor](https://ktor.io) and provides an SSE endpoint for interacting with the agent.

The agent can access:
- Weather data via the Open-Meteo API
- Google Maps functionality via MCP Gateway

## Technologies

This project uses the following technologies:

| Name | Description |
|------|-------------|
| [Koog](https://github.com/koog-ai/koog) | Kotlin framework for building AI agents with tool use capabilities |
| [Ktor](https://ktor.io) | Kotlin web framework for building asynchronous servers and clients |
| [OpenAI API](https://openai.com/api/) | Used for the LLM that powers the agent (GPT-4o Mini) |
| [Ollama](https://ollama.ai/) | Alternative LLM provider for local model execution |
| [Open-Meteo API](https://open-meteo.com/) | Free weather API used by the weather tool |
| [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) | Protocol for tool integration, used for Google Maps tools |
| [TestContainers](https://testcontainers.com/) | Used to run Docker containers for the MCP Gateway |

## Building & Running

### Prerequisites

Before running the application, you need:
1. Set up environment variables in .env or in the terminal (see [.env.template](.env.template))
2. Install docker (for the MCP Gateway container)
3. Run `docker-compose up` to start the database, mcp server, langfuse
4. Open langfuse in http://localhost:3000/ and sign up, then create a new project and add credentials to .env

### Running the Application

To build or run the project, use one of the following tasks:

| Task                          | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `./gradlew run`               | Run the server                                                       |
| `./gradlew buildFatJar`       | Build an executable JAR of the server with all dependencies included |

If the server starts successfully, you'll see output similar to:

```
INFO  Application - Application started in 0.303 seconds.
INFO  Application - Responding at http://0.0.0.0:8080
```

### Using the Application

Once the server is running, you can interact with the AI agent by sending a GET request to:

```
http://localhost:8080/plan?question=YOUR_QUESTION_HERE
```

Or by using IntelliJ's http client [ask-agent-a-question-request.http](ask-agent-a-question-requests.http).

The response will be sent as Server-Sent Events (SSE) that include:
- Tool calls made by the agent
- Results from those tool calls
- The final answer from the agent

## Project Structure

The main components of the project are:

| File | Description |
|------|-------------|
| `Application.kt` | Main application entry point that sets up the Ktor server, configures the LLM clients, and registers the tools |
| `Agent.kt` | Defines the AI agent configuration and event handling |
| `WeatherTool.kt` | Implements the weather tool that fetches data from the Open-Meteo API |
| `McpUtils.kt` | Utility functions for working with the MCP Gateway for Google Maps tools |
