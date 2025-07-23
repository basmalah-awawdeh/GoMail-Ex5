# GoMail - Exercise 5

A full Gmail-like application using *Android ,**Node.js, and a TCP-based **C++ server* with a Bloom filter.  

This project supports multiple users, live email management, looks and works like Gmail.

## Clone the Repository

bash
git clone https://github.com/basmalah-awawdeh/GoMail-Ex5.git

---

## Running the System in Docker

This project supports Docker-based execution via docker-compose.

---

## In The Terminal — Build and Start All Containers

bash
docker-compose up --build

This will build and start the Node.js API container, the C++ server container and the React-Web container.

---

## Example Usage

### Register a New User

Create a new account, and fill your details:
1. Full name
2. Email or username
3. Password (min 8 characters, at least 1 letter, digit and special character from @$!%*?&)
4. Confirm password
5. Choose a profile picture from your computer

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/c7fa9ed1-acef-4d61-b9d9-a8452e8ae2b3" />
---

### Login to the Account

Fill in your username and password
Click Login

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/adce1771-da4d-4c19-8bb5-d526c46c67f9" />
---

### Compose a New Mail

Click compose button
Fill in recipient, subject, body that can optionally include link.
Click Send to send it immediately.
    ==> The mail appears in Sent for the sender.
    ==> Refresh the Inbox, now it appears in Inbox for the recipient.

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/0b05e1b6-9d70-48a3-b41f-6e66e9845548" />
---

### Spam Mail

If a sent mail includes a new URL, it is added to the blacklist.
The blacklist is stored in data/blacklist.txt and data/bloom_filter.txt. These files are automatically loaded and updated by the C++ server.
Future mails with the blacklisted URL will go to the Spam label.

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/0b05e1b6-9d70-48a3-b41f-6e66e9845548" />

<img width="1917" height="1022" alt="Image" src="https://github.com/user-attachments/assets/d54c74c3-85cd-43e3-b709-2eccc5aaef0d" />

If you remove a mail from Spam, the URL is removed from the blacklist



---

### Mark as Important

You can toggle the "star" icon to mark/unmark importance.
Click "Important" to see all mails you’ve marked as important.


---

### Labels (Categories)

Click "+ New Label" to create a new custom label.
You can attach mails to these labels.

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/bc417928-e88a-47a5-9152-68160a4b758f" />
---

### Delete Mail

You can delete a mail.
It is removed only for you, not for the other user.

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/0cf9f859-448f-4818-8a3b-34d0b5c4b8f4" />
---

### Search Mail

Use the top search bar to search for a specific word in the mail.
Results update in real time.

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/742955a4-45a4-41f6-9d19-2b6c10a1d59f" />

<img width="1080" height="1920" alt="Image" src="https://github.com/user-attachments/assets/ed2b7aec-50e4-4dd2-bda7-e6a6e3bb6030" />
---


###  Logout

Click your profile picture and then select Logout to exit your session.

