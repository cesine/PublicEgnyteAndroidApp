

# Usage

### Set up your own Egnyte key

Your Egnyte key is located in the local.properties file of your project (this ensures your key remains private if you are using a public Git repo). Create a file if you don't already have one and add a line `EgnyteAPIKey=PUT-YOUR-KEY-HERE` in the file (where `PUT-YOUR-KEY-HERE` is your key).

```bash
$ echo "EgnyteAPIKey=PUT-YOUR-KEY-HERE" >> local.properties
```