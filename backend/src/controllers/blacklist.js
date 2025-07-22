import { v4 as uuidv4 } from 'uuid'; 
import { _injectBlacklist } from './mails.js';
import Blacklist from '../models/Blacklist.js'; 
import net from 'net';

//communicating with cpp server port, or default 8081
const CPP_SERVER_PORT = process.env.CPP_SERVER_PORT || 8081;

//sending commands to cpp server
export function sendToCppServer(url, mode = 'CHECK') {
  const methodMap = {
    'ADD': 'POST',
    'CHECK': 'GET',
    'REMOVE': 'DELETE'
  };
  const command = methodMap[mode];
  if (!command){
    throw new Error('Invalid command mode for C++ server');
  }
  return new Promise((resolve, reject) => {
    //creating tcp connection with cpp server
    const client = net.createConnection({ host: 'cpp-server', port: parseInt(CPP_SERVER_PORT) }, () => {
      //sending command and url
      client.write(`${command} ${url}\n`);
    });
    client.on('data', (data) => {
      //reading the response and closing the connection
      const response = data.toString().trim();
      client.end();
      resolve(response);
    });
    client.on('error', (err) => {
      reject(new Error('Could not connect to C++ server: ' + err.message));
    });
  });
}

//adding url to the blacklist
export const addBlacklistedLink = async (req, res) => {
  const { url } = req.body;

  //url validation
  if (!url){
    return res.status(400).json({ error: 'Missing url' });
  }

  try {
    //asking cpp server to add url
    const cppResponse = await sendToCppServer(url, 'ADD');

    //creating entry if it doesn't exist in MongoDB
    let entry = await Blacklist.findOne({ url });
    if (!entry){
      entry = await Blacklist.create({ url });
      //updating the mail with new blacklist
      const all = await Blacklist.find();
      _injectBlacklist(all.map(l => l.url));
    }

    //determining response according to cpp server response
    const isNew = cppResponse.startsWith('201');
    const status = isNew ? 201 : 200;

    //blacklist entry
    return res.status(status).json({
      //write a message according to if the url is new or already exists
      message: isNew ? 'URL added to blacklist' : 'URL already blacklisted',
      id: entry._id,
      url
    });

  } catch (err) {
    //server error
    console.error('C++ server error:', err.message);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

//removing blacklisted url from MongoDB and blacklist by id
export const deleteBlacklistedLink = async (req, res) => {
  const { id } = req.params;

  try {
    //finding blacklist entry
    const entry = await Blacklist.findById(id);
    if (!entry){
      return res.status(404).json({ error: 'Link not found' });
    }

    const url = entry.url;

    //asking cpp server to remove the url
    const cppResponse = await sendToCppServer(url, 'REMOVE');

    //removing is confirmed
    if (cppResponse.startsWith('204')){
      //removing from MongoDB
      await Blacklist.findByIdAndDelete(id);

      //updating with the new list
      const all = await Blacklist.find();
      _injectBlacklist(all.map(l => l.url));

      return res.status(204).end();
    } else {
      //removing failed
      return res.status(400).json({ error: 'C++ server failed to remove URL: ' + cppResponse });
    }

  } catch (err) {
    //server error
    console.error('C++ server error:', err.message);
    return res.status(500).json({ error: 'Internal server error' });
  }
};
