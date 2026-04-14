import express from "express";
import { deleteWorkflow, 
    getWorkflowById, 
    listWorkflows ,
    updateWorkflow,
} from "../controllers/workflow.controller.js";

const router = express.Router();

router.get("/workflows", listWorkflows);
router.get("/workflows/:id", getWorkflowById);
router.delete("/workflows/:id", deleteWorkflow);
router.patch("/workflows/:id",updateWorkflow);

export default router;

