let currentStep = 1;
const totalSteps = 6;

const progressBar = document.getElementById("progressBar");
const progressText = document.getElementById("progressText");
const steps = document.querySelectorAll(".form-step");
const nextBtn = document.getElementById("nextBtn");
const prevBtn = document.getElementById("prevBtn");

function showStep(step) {
  steps.forEach((el) => {
    el.classList.toggle("hidden", el.dataset.step != step);
  });

  const progressPercent = (step / totalSteps) * 100;
  progressBar.style.width = progressPercent + "%";

  const titles = [
    "Project Location",
    "Basic Information",
    "Project Themes",
    "Contact Information",
    "Budget & Objectives",
    "Supporting Documents",
  ];

  progressText.textContent = `Step ${step} of ${totalSteps}: ${
    titles[step - 1]
  }`;

  prevBtn.style.display = step === 1 ? "none" : "inline-flex";
  const isLastStep = step === totalSteps;
  nextBtn.textContent = isLastStep ? "Submit" : "Next";
  console.log("📍 Step", step, "of", totalSteps, "- button text:", nextBtn.textContent, "- is last step:", isLastStep);
}

nextBtn.addEventListener("click", () => {
  console.log("🔘 Next button clicked!");
  console.log("🔘 current step:", currentStep, "total steps:", totalSteps);
  
  try {
    if (currentStep < totalSteps) {
      console.log("🔘 Going to next step");
      currentStep++;
      showStep(currentStep);
    } else {
      console.log("🔘 On last step, triggering form submission");
      // On last step, trigger form submission
      const form = document.getElementById("projectForm");
      console.log("🔘 Form lookup result:", form);
      if (form) {
        console.log("🔘 Form found, dispatching submit event");
        const submitEvent = new Event("submit", { bubbles: true, cancelable: true });
        console.log("🔘 Submit event created:", submitEvent);
        const dispatchResult = form.dispatchEvent(submitEvent);
        console.log("🔘 Submit event dispatched, result:", dispatchResult);
      } else {
        console.error("🔘 Form not found!");
      }
    }
  } catch (error) {
    console.error("🔘 Error in next button click handler:", error);
  }
});

prevBtn.addEventListener("click", () => {
  if (currentStep > 1) {
    currentStep--;
    showStep(currentStep);
  }
});

// Initialize
showStep(currentStep);
