# A Matrix chat client

![screenshot](https://github.com/dzaima/dzaima.github.io/blob/master/images/chatClient.png)

### Usage

1. `./build.py`
2. make `accounts/profile.json` with:
   ```js
   {"accounts": [
     {
       "type": "matrix",
       "server": "https://matrix.org", // or your homeserver
       "userid": "@example:matrix.org",
       "password": "yourPassword" // or "token": "tokenToUse"
     }
     // you can add more accounts here
   ]}
   ```
3. `./run` (alternatively, `./run path/to/alternateProfile.json`)

### keybindings

|                           key | action                                         |
|------------------------------:|------------------------------------------------|
|                 <kbd>up</kbd> | edit previous message                          |
|               <kbd>down</kbd> | edit next message (or stop editing if at last) |
|             <kbd>alt+up</kbd> | reply to message above current                 |
|           <kbd>alt+down</kbd> | reply to message below current                 |
|          <kbd>alt+click</kbd> | reply to currently hovered over message        |
|                <kbd>esc</kbd> | cancel reply/edit/message                      |
| <kbd>ctrl+alt+shift+del</kbd> | delete message currently selected for editing  |
|            <kbd>ctrl+up</kbd> | go to room above                               |
|          <kbd>ctrl+down</kbd> | go to room below                               |
|          <kbd>ctrl+plus</kbd> | increase UI scale                              |
|         <kbd>ctrl+minus</kbd> | decrease UI scale                              |
|              <kbd>alt+r</kbd> | hide/unhide unread count for current room      |
|         <kbd>ctrl+alt+r</kbd> | hide/unhide unread count for all rooms         |

Most of those (along with some other things) are options in `res/chat.dzcfg`.

While `res/chat.dzcfg` can be directly modified, it may be better to make a file `local.dzcfg` (in the root folder of the project) and copy the necessary fields in.

You can reload the config with <kbd>f5</kbd>, though some things may need refreshing the changed thing (by e.g. selecting clicking a room to refresh message list), or restarting the app completely.



### syntax
````
by default, messages are interpreted as a markdown-ish thing. This default can be changed in res/chat.dzcfg `markdown = true`

/md forced *markdown*
/text forced plain text (also "/plain ..." works)
/me does something
file uploading is a very temporary & unfinished thing and just gives you a link to the image

_italics_
*bold*
\*text surrounded by asterisks\*
[link text](https://example.org)
---strikethrough--- (alternatively, ~~strikethrough~~)
||spoiler||
`code block`
`backticks: \` inside code block`
`` alternatively: ` in doubled backticks ``
``` that continues for any number of backticks ```
select some text and press "`" to automatically escape things as needed

```java
public static void main(String[] args) {
  System.out.println("codeblock with language");
}
```
````