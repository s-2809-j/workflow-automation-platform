import mongoose from 'mongoose';
const ExecutionLogSchema=new mongoose.Schema(
    {
        workflowId:{type:mongoose.Schema.Types.ObjectId,required:true,index:true},
        runId:{type:String,required:true,index:true},
        status:{type:String,enum:["success","failed"],required:true},
        errorType:{type:String,default:""},
        durationMs:{type:Number,default:0},
        records:{type:Number,default:0},
        meta:{type:Object,default:{}},
    },
    {timestamps:true},
);

export const ExecutionLog=mongoose.model("ExecutionLog",ExecutionLogSchema);