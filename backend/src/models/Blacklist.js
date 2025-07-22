import mongoose from 'mongoose';

const blacklistSchema = new mongoose.Schema({
  url: { type: String, required: true, unique: true }
});

export default mongoose.model('Blacklist', blacklistSchema);
