import { ZodError } from "zod";

export function errorHandlerMiddleware(err, req, res, next) {
  // 🔹 Handle Zod Validation Errors
  if (err instanceof ZodError) {
    return res.status(400).json({
      status: "error",
      code: "VALIDATION_ERROR",
      message: err.errors.map(e => e.message).join(", "),
      requestId: req.requestId,
    });
  }

  // 🔹 Default Error Handling
  const httpStatus = err.httpStatus || 500;

  res.status(httpStatus).json({
    status: "error",
    code: err.code || "INTERNAL_ERROR",
    message: err.message || "INTERNAL_SERVER_ERROR",
    requestId: req.requestId,
  });
}