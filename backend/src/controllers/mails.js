import { v4 as uuidv4 } from 'uuid';
import Email from '../models/Email.js';
import User from '../models/User.js';
import { sendToCppServer } from './blacklist.js';
import net from 'net';
import Blacklist from '../models/Blacklist.js'; 


let blacklistUrls = [];
const CPP_SERVER_PORT = process.env.CPP_SERVER_PORT || 8081;

export const _injectBlacklist = (bl) => {
  blacklistUrls = bl;
};

export const getMails = async (req, res) => {
  try {
    const result = await Email.find({
      $and: [
        { $or: [{ to: req.userId }, { from: req.userId }] },
        { deletedBy: { $ne: req.userId } }
      ]
    })
    .sort({ timestamp: -1 })
    .limit(50);

    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};


//sending mail or saving drfat
export const sendMail = async (req, res) => {
  const { to, subject, body, links, sent, timestamp } = req.body;

  if (!to || !body) {
    return res.status(400).json({ error: 'Missing recipient or body' });
  }

  // getting recipient by username
  const recipient = await User.findOne({ username: to });
  if (!recipient) {
    return res.status(400).json({ error: 'Recipient not found' });
  }

  //getting sender by its id
  const sender = await User.findById(req.userId);
  if (!sender) {
    return res.status(403).json({ error: 'Invalid sender' });
  }

  let spamLabel = false;
  if (sent) {
    try {
      for (const link of links || []) {
        const result = await checkWithCppServer(link);
        if (result === 'BLACKLISTED') {
          spamLabel = true;
          break;
        }
      }
    } catch (err) {
      console.error('C++ server error:', err.message);
      return res.status(500).json({ error: 'Spam check failed' });
    }
  }

  const mail = new Email({
    from: sender._id,
    fromName: sender.username,
    to: recipient._id,
    toName: recipient.username,
    subject,
    body,
    links,
    sent: !!sent,
    labels: spamLabel ? { [recipient._id]: ['spam'] } : {},
    customLabels: {},
    deletedBy: [],
    timestamp: timestamp || new Date().toISOString()
  });

  await mail.save();
  res.status(201).end();
};


export const getMailById = async (req, res) => {
  try {
    const mail = await Email.findById(req.params.id);
    if (!mail || (mail.from.toString() !== req.userId && mail.to.toString() !== req.userId)) {
      return res.status(403).json({ error: 'Unauthorized or mail not found' });
    }
    res.json(mail);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};


export const updateMail = async (req, res) => {
  try {
    const mail = await Email.findById(req.params.id);
    const userKey = String(req.userId);

    if (!mail) return res.status(404).json({ error: 'Mail not found' });

    const isSender = mail.from.toString() === req.userId;
    const isRecipient = mail.to.toString() === req.userId;

    //allowing only the sender or recipient to modify
    if (!isSender && !isRecipient) {
      return res.status(403).json({ error: 'Unauthorized' });
    }

    // toggle system labels (importnat, spam)
    if (req.body.label) {
      if (!mail.labels) mail.labels = new Map();

      //initializing array if undefined
      if (!Array.isArray(mail.labels.get(userKey))) mail.labels.set(userKey, []);

      const currentLabels = mail.labels.get(userKey);
      const label = req.body.label;

      //only the recipient can mark as spam
      if (label === 'spam' && !isRecipient) {
        return res.status(403).json({ error: 'Only the recipient can mark as spam' });
      }

      // toggle the label
      if (currentLabels.includes(label)) {
        //removing label
        const filtered = currentLabels.filter(l => l !== label);
        mail.labels.set(userKey, filtered);

        //unmarking spam ==> clear all the labels for this user and remove links from blacklist
        if (label === 'spam') {
          mail.labels.set(userKey, []);
          if (!mail.customLabels) mail.customLabels = new Map();
          mail.customLabels.set(userKey, []);
          for (const link of mail.links || []) {
            try {
              await sendToCppServer(link, 'REMOVE');
            } catch (err) {
              console.error(`Failed to remove link from blacklist: ${link}`, err.message);
            }
          }
        }
      } else {
        //adding label
        if (label === 'spam') {
          mail.labels.set(userKey, ['spam']);
          if (!mail.customLabels) mail.customLabels = new Map();
          mail.customLabels.set(userKey, []);
          

          for (const link of mail.links || []) {
            try {
              await sendToCppServer(link, 'ADD');
              const exist = await Blacklist.findOne({url: link});
              if (!exist) {
                await Blacklist.create({ url: link });
              }
              const all = await Blacklist.find();
              _injectBlacklist(all.map(b => b.url));
            } catch (err) {
              console.error(`Failed to add link to blacklist: ${link}`, err.message);
            }
          }
        } else {
          mail.labels.set(userKey, [...currentLabels, label]);
        }
      }
      mail.markModified('labels');
      await mail.save();
      return res.status(204).end();
    }

    //only the sender can edit mail cotnent
    if (!isSender && (
      'subject' in req.body ||
      'body' in req.body ||
      'to' in req.body ||
      'links' in req.body
    )) {
      return res.status(403).json({ error: 'Only the sender can edit this mail' });
    }

    //updating customed labels for each user
    if ('customLabels' in req.body) {
      if (!mail.customLabels) mail.customLabels = new Map();
      
      const userKey = String(req.userId);
      const labels = Array.isArray(req.body.customLabels) ? req.body.customLabels : [];

      mail.customLabels.set(userKey, labels);
      mail.markModified('customLabels');
    }

    //allowed fields for sender
    const allowedFields = ['subject', 'body', 'links'];
    for (const key of allowedFields) {
      if (key in req.body) {
        mail[key] = req.body[key];
      }
    }

    //sending a draft
    if (req.body.sent === true && !mail.sent) {
      const recipientUsername = (req.body.to || '').trim();
      const recipient = await User.findOne({ username: recipientUsername });
      if (!recipient) {
        return res.status(400).json({ error: `Recipient '${recipientUsername}' not found` });
      }
      mail.to = recipient.id;
      mail.toName = recipient.username;

      try {
        let isSpam = false;
        for (const link of mail.links || []) {
          const result = await checkWithCppServer(link);
          if (result === 'BLACKLISTED') {
            isSpam = true;
            break;
          }
        }
        if (isSpam) {
          if (!mail.labels) mail.labels = new Map();
          if (!Array.isArray(mail.labels.get(recipient.id))) mail.labels.set(recipient.id, []);
          if (!mail.labels.get(recipient.id).includes('spam')) mail.labels.set(recipient.id, ['spam']);
        }
      } catch (err) {
        console.error('C++ server error:', err.message);
        return res.status(500).json({ error: 'Spam check failed' });
      }
      mail.sent = true;
    }

    await mail.save();
    return res.status(204).end();

  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};



export const deleteMail = async (req, res) => {
  try{
    const mail = await Email.findById(req.params.id);
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found' });
    }
    if (mail.from.toString() !== req.userId && mail.to.toString() !== req.userId) {
      return res.status(403).json({ error: 'Unauthorized' });
    }
    if (!Array.isArray(mail.deletedBy)) {
      mail.deletedBy = [];
    }
    if (!mail.deletedBy.includes(req.userId)) {
      mail.deletedBy.push(req.userId);
    }
    //both sides deleted the mail ==> deleting
    const fromDeleted = mail.deletedBy.includes(mail.from.toString());
    const toDeleted = mail.deletedBy.includes(mail.to.toString());
    const isSelfSent = mail.from.toString() === mail.to.toString();
    if ((fromDeleted && toDeleted) || (isSelfSent && fromDeleted)) {
      await mail.deleteOne();
      console.log(`Mail ${mail._id} permanently deleted`);
    }else{
      await mail.save();
    }
    res.status(204).end();
  }catch(err){
    res.status(500).json({ error: err.message });
  } 
};

export const searchMails = async (req, res) => {
  try{
    const query = req.params.query.toLowerCase();
    const mails = await Email.find({
      $or: [
        { subject: { $regex: query, $options: 'i' } },
        { body: { $regex: query, $options: 'i' } },
        { toName: { $regex: query, $options: 'i' } },
        { fromName: { $regex: query, $options: 'i' } }
      ]
    });
    res.json(result);
  }catch (err){
    res.status(500).json({ error: err.message});
  }
};

function checkWithCppServer(url) {
  return new Promise((resolve, reject) => {
    const client = net.createConnection({ host: 'cpp-server', port: parseInt(CPP_SERVER_PORT) }, () => {
      client.write(`GET ${url}\n`);
    });
    client.on('data', (data) => {
      const response = data.toString().trim();
      client.end();
      if (response.includes('true')) {
        resolve("BLACKLISTED");
      } else {
        resolve("OK");
      }
    });
    client.on('error', (err) => {
      reject(err);
    });
  });
}
