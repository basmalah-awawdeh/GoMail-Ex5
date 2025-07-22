import mongoose from "mongoose";

const labelSchema = new mongoose.Schema({
    name: { type : String, required : true},
    owner: { type : String, required : true}
});

const Label = mongoose.model('Label', labelSchema);
export default Label;