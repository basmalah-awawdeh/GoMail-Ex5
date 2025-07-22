import { v4 as uuidv4 } from 'uuid';

import Label from '../models/Label.js';

export const getLabels = async (req, res) => {
  try{
    const labels = await Label.find({owner : req.userId });
    res.status(200).json(labels);
  }catch (err){
    res.status(500).json({ error : 'Failed to fetch labels' });
  }
};

export const createLabel = async (req, res) => {
  const { name } = req.body;

  //return error if no name was provided
  if (!name){
    return res.status(400).json({ error: 'Name is required' });
  }

  //creatimg and store the new label
  try{
    const label = new Label({ name, owner : req.userId });
    await label.save();
    res.status(201).location(`/api/labels/${label._id}`).json(label);
  }catch (err){
    res.status(500).json({ error : 'Failed to create label' });
  }
};

export const getLabelById = async (req, res) => {
  //finding the label by id
  try{
    const label = await Label.findOne({ _id: req.params.id, owner: req.userId });
    //if the label not fount return 404
    if(!label){
      return res.status(404).json({ error: 'Label not found' });
    }
    //return the label in json
    res.json(label);
  }catch (err) {
    res.status(500).json({ error: 'Failed to fetch label' });
  }
};

export const updateLabel = async (req, res) => {
  //finding the label by id
  try{
    const label = await Label.findByIdAndUpdate(
      { _id: req.params.id, owner: req.userId },
      { name: req.body.name },
      { new: true }
    );
    //if the label is not exist
    if(!label){
      return res.status(404).json({ error: 'Label not found'});
    }
    //responding 204 No Content, success with no response body
    res.status(204).end();
  }catch (err) {
    res.status(500).json({ error: 'Failed to update label' });
  }
};

export const deleteLabel = async (req, res) => {
  try{
    //delete the label
    const result = await Label.deleteOne({ _id: req.params.id, owner: req.userId });
    
    //if the label is not exist
    if(result.deletedCount === 0){
      return res.status(404).json({ error: 'Label not found' });
    }

    //responding 204 No Content, success with no response body
    res.status(204).end();
  }catch (err) {
    res.status(500).json({ error: 'Failed to delete label' });
  }
};
