import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';
import User from '../models/User.js';
//signing jwt tokne
const SECRET = 'your-secret-key';

export const registerUser = async (req, res) => {
  try {
    const { username, password, name } = req.body;
    const imageFile = req.file;
    if (!username || !password || !imageFile) {
      return res.status(400).json({ error: 'Missing username, password, or image' });
    }
    //checking if the user exists in MongoDB
    const existingUser = await User.findOne({ username });
    if (existingUser) {
      return res.status(409).json({ error: 'Username already taken' });
    }
    //creating and saving th user in MongoDB
    const newUser = new User({
      username,
      password,
      name: name || '',
      imageUrl: `/uploads/${imageFile.filename}`
    });
    await newUser.save();
    // making jwt for 2 hours
    const token = jwt.sign({ id: newUser._id }, SECRET, { expiresIn: '2h' });
    //respond with data without password
    res.status(201).json({
      token,
      user: {
        id: newUser._id,
        username: newUser.username,
        name: newUser.name,
        image: newUser.imageUrl
      }
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};

export const getUser = async (req, res) => {
  try {
    const user = await User.findById(req.params.id).select('-password');
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json(user);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};


export const loginUser = async (req, res) => {
  console.log("LOGIN REQUEST BODY:", req.body); 

  try {
    const { username, password } = req.body;
    const user = await User.findOne({ username, password });
    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }
    const token = jwt.sign({ id: user._id }, SECRET, { expiresIn: '2h' });
    res.json({
      token,
      user: {
        id: user._id,
        username: user.username,
        name: user.name,
        image: user.imageUrl
      }
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
};
