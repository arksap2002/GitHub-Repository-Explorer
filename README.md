# GitHub-Repository-Explorer

<!-- Plugin description -->
GitHub-Repository-Explorer is an IntelliJ-based plugin that lets you browse any GitHub repository directly inside your IDE.

- Log in with a GitHub Personal Access Token (PAT)
- Open any repository by owner/name
- Explore the repository structure via a tree with lazy loading
- View text files (opened as read-only tabs) and preview image files
- Works with public repos (no special scopes) and private repos (requires token with repo scope)
<!-- Plugin description end -->

## Table of Contents
- [Features](#features)
- [Demo](#demo)
- [Usage](#usage)
  - [Log In](#log-in)
  - [Open a Repository](#open-a-repository)
  - [Browse & Open Files](#browse--open-files)
  - [Log Out](#log-out)
- [Personal Access Token (PAT) scopes](#personal-access-token-pat-scopes)

## Features
- Tools menu integration: GitHub Repository Explorer group with three actions: Log In, Log Out, Open a GitHub Repo
- Validates owner and repository on GitHub before opening
- Lazily loads directory contents as you expand the tree nodes
- Opens text files as read-only editor tabs
- Opens common image files (png, jpg and jpeg) in the IDE
- Uses GitHub API under the hood (via Ktor) with your token to avoid low rate limits and to access private repositories

## Demo

![Demo animation](readme-resources/Demo.gif)

## Usage
The plugin adds a new group "GitHub Repository Explorer" under the Tools menu with the following actions:
- Log In
- Log Out
- Open a GitHub Repo

### Log In
Use Tools > GitHub Repository Explorer > Log In
- Paste your GitHub Personal Access Token (PAT)
- The plugin validates the token via GitHub API; if valid, it is stored using an application-level PersistentStateComponent
- You can proceed without a token for public repositories, but login is recommended to avoid rate limits

### Open a Repository
Use Tools > GitHub Repository Explorer > Open a GitHub Repo
- Enter the repository owner (e.g., jetbrains) and name (e.g., intellij-community)
- The plugin validates:
  - That the owner exists
  - That the repository exists under that owner
- On success, a Repository Structure dialog opens with a tree view of the repo’s contents

### Browse & Open Files
- Expand directories to lazily load their contents from GitHub (only loaded when expanded)
- Select a file node to fetch and open it in the editor:
  - Text files open as read-only tabs
  - Images open in a preview tab
- If an item has no download URL (rare for certain GitHub entries), an error message is shown

### Log Out
Use Tools > GitHub Repository Explorer > Log Out to clear the saved token

## Personal Access Token (PAT) scopes
- Public repositories: no special scopes required; token is optional but helps with rate limits
- Private repositories: a PAT with repo scope is required
- Create/manage tokens at https://github.com/settings/tokens (classic tokens) or Developer settings for fine-grained tokens. For fine-grained tokens, grant Read access to Repository contents for the desired repositories
