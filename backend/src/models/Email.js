import mongoose from 'mongoose';

const EmailSchema = new mongoose.Schema({
  from: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  fromName: { type: String },
  to: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  toName: { type: String },
  subject: { type: String },
  body: { type: String },
  links: { type: [String], default: [] },
  sent: { type: Boolean, default: false },
  labels: { type: Map, of: [String], default: {} },         
  customLabels: { type: Map, of: [String], default: {} },   
  deletedBy: { type: [String], default: [] },
  timestamp: { type: String, default: () => new Date().toISOString() }
});

const Email = mongoose.model('Email', EmailSchema);
export default Email;
