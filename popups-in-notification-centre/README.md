# Notifications: Pop-ups in the Centre

Bitwig's pop-up notifications (the short messages in the middle of the window, from Bitwig or from a mod) show as messages in the notification centre at the top right instead.

- Each pop-up becomes an information message with a close button. It opens the centre's overlay, so it is seen as the pop-up was, and goes by itself after 8 seconds.
- Its title is the text before the first colon, when that is at most 60 characters and text follows ("Voice Lead Chords: select the chords..." reads "Voice Lead Chords" over "select the chords..."). Otherwise the title is "Notification" over the whole text.
- The same text again within 5 seconds replaces the message instead of adding another.
- A controller script's pop-up (a knob's value as it turns) stays Bitwig's pop-up.

With Notifications: History, the pop-ups are kept in the history like every other message.

Requires Framework: Notifications 0.3.0-dev.1 or later. Turning the mod off gives the pop-ups back to Bitwig.
