const $ = (selector) => document.querySelector(selector);
const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

const state = {
  config: null,
  stream: null,
  classIndex: 0,
  selectedRepetition: 1,
  progress: {},
  busy: false,
  stopRequested: false,
};

const noSignPrompts = [
  "손을 내리고 정면을 보며 가만히 있기",
  "머리나 얼굴을 자연스럽게 만지기",
  "옷이나 소매를 자연스럽게 정리하기",
  "수어가 아닌 일반적인 손짓하기",
  "양손을 가볍게 움직인 뒤 내리기",
  "수어 시작 전 준비 동작만 하기",
  "손을 화면 밖으로 잠시 이동하기",
  "한 손으로 주변 물건을 가리키기",
];

function currentClass() { return state.config.classes[state.classIndex]; }
function selectedSigner() { return $("#signer-id").value; }
function sessionRange() {
  return $("#session-number").value === "1" ? [1, 10] : [11, 20];
}

function setMessage(text, type = "") {
  const element = $("#message");
  element.textContent = text;
  element.className = `message ${type}`;
}

async function loadProgress() {
  const response = await fetch(`/api/progress?signerId=${encodeURIComponent(selectedSigner())}`);
  if (!response.ok) throw new Error("진행률을 불러오지 못했습니다.");
  state.progress = (await response.json()).progress;
  const [start] = sessionRange();
  state.selectedRepetition = start;
  render();
}

function render() {
  const item = currentClass();
  const completed = state.progress[item.stableKey] || [];
  const total = Object.values(state.progress).reduce((sum, values) => sum + values.length, 0);
  $("#stable-key").textContent = item.stableKey;
  $("#class-label").textContent = item.label;
  $("#class-progress").textContent = `${completed.length} / 20`;
  $("#total-progress").textContent = `${total} / 220`;
  $("#progress-fill").style.width = `${(total / 220) * 100}%`;
  $("#delete-recording").disabled = !completed.includes(state.selectedRepetition) || state.busy;
  renderClassList();
  renderRepetitions();
  renderInstruction();
}

function renderClassList() {
  const list = $("#class-list");
  list.replaceChildren();
  state.config.classes.forEach((item, index) => {
    const count = (state.progress[item.stableKey] || []).length;
    const button = document.createElement("button");
    button.className = `class-item ${index === state.classIndex ? "active" : ""} ${count === 20 ? "complete" : ""}`;
    button.innerHTML = `<span>${item.label}</span><span>${count}/20</span>`;
    button.disabled = state.busy;
    button.addEventListener("click", () => {
      state.classIndex = index;
      const [start, end] = sessionRange();
      state.selectedRepetition = firstMissing(item.stableKey, start, end) || start;
      render();
    });
    list.append(button);
  });
}

function renderRepetitions() {
  const grid = $("#repetition-grid");
  const completed = state.progress[currentClass().stableKey] || [];
  const [start, end] = sessionRange();
  grid.replaceChildren();
  for (let repetition = 1; repetition <= 20; repetition += 1) {
    const button = document.createElement("button");
    button.textContent = String(repetition).padStart(2, "0");
    button.className = [
      "rep-button",
      repetition >= start && repetition <= end ? "in-session" : "",
      completed.includes(repetition) ? "done" : "",
      repetition === state.selectedRepetition ? "selected" : "",
    ].join(" ");
    button.disabled = state.busy;
    button.addEventListener("click", () => {
      state.selectedRepetition = repetition;
      render();
    });
    grid.append(button);
  }
}

function renderInstruction() {
  const item = currentClass();
  if (item.stableKey === "NO_SIGN") {
    $("#instruction").textContent = `NO_SIGN 예시: ${noSignPrompts[(state.selectedRepetition - 1) % noSignPrompts.length]}`;
  } else {
    const instruction = $("#instruction");
    instruction.textContent = `${item.label}: 대기 자세 → 수어 동작 → 대기 자세를 녹화 시간 안에 수행하세요. `;

    if (item.link) {
      const exampleLink = document.createElement("a");
      exampleLink.href = item.link;
      exampleLink.target = "_blank";
      exampleLink.rel = "noopener noreferrer";
      exampleLink.textContent = "예시 영상 보기";
      instruction.append(exampleLink);
    }
  }
}

function firstMissing(stableKey, start, end) {
  const completed = state.progress[stableKey] || [];
  for (let value = start; value <= end; value += 1) {
    if (!completed.includes(value)) return value;
  }
  return null;
}

async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    throw new Error("이 브라우저는 카메라 녹화를 지원하지 않습니다. 최신 Chrome, Edge 또는 Safari를 사용하세요.");
  }
  if (state.stream) state.stream.getTracks().forEach((track) => track.stop());
  const deviceId = $("#camera-select").value;
  state.stream = await navigator.mediaDevices.getUserMedia({
    video: {
      deviceId: deviceId ? { exact: deviceId } : undefined,
      width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30, max: 30 },
    },
    audio: false,
  });
  $("#preview").srcObject = state.stream;
  $("#camera-placeholder").classList.add("hidden");
  $("#camera-button").textContent = "카메라 다시 시작";
  $("#record-one").disabled = false;
  $("#record-session").disabled = false;
  await refreshCameras();
  setMessage("카메라 준비가 완료됐습니다.", "success");
}

async function refreshCameras() {
  const devices = (await navigator.mediaDevices.enumerateDevices()).filter((item) => item.kind === "videoinput");
  const select = $("#camera-select");
  const previous = select.value;
  select.innerHTML = '<option value="">기본 카메라</option>';
  devices.forEach((device, index) => {
    const option = document.createElement("option");
    option.value = device.deviceId;
    option.textContent = device.label || `카메라 ${index + 1}`;
    select.append(option);
  });
  select.value = previous;
}

function recorderOptions() {
  const candidates = ["video/webm;codecs=vp9", "video/webm;codecs=vp8", "video/webm", "video/mp4"];
  const mimeType = candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate));
  if (!mimeType) throw new Error("지원되는 녹화 형식(WebM/MP4)이 없습니다.");
  return { mimeType, videoBitsPerSecond: 2_500_000 };
}

async function countdown(seconds) {
  const overlay = $("#countdown");
  overlay.classList.remove("hidden");
  for (let value = seconds; value > 0; value -= 1) {
    overlay.textContent = value;
    await sleep(1000);
    if (state.stopRequested) throw new Error("촬영이 중단됐습니다.");
  }
  overlay.classList.add("hidden");
}

async function recordBlob(seconds) {
  const chunks = [];
  const recorder = new MediaRecorder(state.stream, recorderOptions());
  recorder.addEventListener("dataavailable", (event) => { if (event.data.size) chunks.push(event.data); });
  const stopped = new Promise((resolve, reject) => {
    recorder.addEventListener("stop", resolve, { once: true });
    recorder.addEventListener("error", () => reject(new Error("브라우저 녹화에 실패했습니다.")), { once: true });
  });
  $("#recording-indicator").classList.remove("hidden");
  recorder.start(250);
  await sleep(seconds * 1000);
  recorder.stop();
  await stopped;
  $("#recording-indicator").classList.add("hidden");
  return new Blob(chunks, { type: recorder.mimeType });
}

async function uploadRecording(blob, repetition, overwrite) {
  const response = await fetch("/api/recording", {
    method: "POST",
    headers: {
      "Content-Type": blob.type,
      "X-Signer-Id": selectedSigner(),
      "X-Stable-Key": currentClass().stableKey,
      "X-Repetition": String(repetition),
      "X-Overwrite": String(overwrite),
    },
    body: blob,
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || "영상 저장에 실패했습니다.");
}

function setBusy(busy, automatic = false) {
  state.busy = busy;
  $("#signer-id").disabled = busy;
  $("#session-number").disabled = busy;
  $("#camera-select").disabled = busy;
  $("#camera-button").disabled = busy;
  $("#record-one").disabled = busy || !state.stream;
  $("#record-session").disabled = busy || !state.stream;
  $("#stop-auto").classList.toggle("hidden", !automatic);
  render();
}

async function capture(repetition, overwrite = false) {
  state.selectedRepetition = repetition;
  renderInstruction();
  setMessage(`${currentClass().label} ${String(repetition).padStart(2, "0")}번 준비`);
  await countdown(Number($("#prep-seconds").value));
  setMessage("녹화 중입니다. 동작을 수행하세요.");
  const blob = await recordBlob(Number($("#record-seconds").value));
  setMessage("로컬 컴퓨터에 저장 중입니다.");
  await uploadRecording(blob, repetition, overwrite);
  await loadProgress();
  setMessage(`${currentClass().label} ${String(repetition).padStart(2, "0")}번 저장 완료`, "success");
}

async function recordOne() {
  const completed = state.progress[currentClass().stableKey] || [];
  const overwrite = completed.includes(state.selectedRepetition);
  if (overwrite && !confirm(`${state.selectedRepetition}번 영상을 덮어쓰고 다시 촬영할까요?`)) return;
  state.stopRequested = false;
  setBusy(true);
  try { await capture(state.selectedRepetition, overwrite); }
  catch (error) { setMessage(error.message, "error"); }
  finally { $("#countdown").classList.add("hidden"); $("#recording-indicator").classList.add("hidden"); setBusy(false); }
}

async function recordCurrentSession() {
  const [start, end] = sessionRange();
  const missing = [];
  for (let repetition = start; repetition <= end; repetition += 1) {
    if (!(state.progress[currentClass().stableKey] || []).includes(repetition)) missing.push(repetition);
  }
  if (!missing.length) { setMessage("현재 세션의 10개 영상이 모두 완료됐습니다.", "success"); return; }
  state.stopRequested = false;
  setBusy(true, true);
  try {
    for (const repetition of missing) {
      if (state.stopRequested) break;
      await capture(repetition, false);
      await sleep(700);
    }
    setMessage(state.stopRequested ? "자동 촬영을 중단했습니다." : "현재 수어의 세션 촬영을 완료했습니다.", "success");
  } catch (error) { setMessage(error.message, "error"); }
  finally { $("#countdown").classList.add("hidden"); $("#recording-indicator").classList.add("hidden"); setBusy(false); }
}

async function deleteSelected() {
  if (!confirm(`${currentClass().label} ${state.selectedRepetition}번 영상을 삭제할까요?`)) return;
  const query = new URLSearchParams({ signerId: selectedSigner(), stableKey: currentClass().stableKey, repetition: state.selectedRepetition });
  const response = await fetch(`/api/recording?${query}`, { method: "DELETE" });
  const payload = await response.json();
  if (!response.ok) { setMessage(payload.error || "삭제하지 못했습니다.", "error"); return; }
  await loadProgress();
  setMessage("선택한 영상을 삭제했습니다.", "success");
}

async function initialize() {
  try {
    const response = await fetch("/api/config");
    if (!response.ok) throw new Error("로컬 서버에 연결할 수 없습니다.");
    state.config = await response.json();
    $("#server-status").textContent = "로컬 저장 준비 완료";
    $("#server-status").classList.add("ok");
    await loadProgress();
  } catch (error) {
    setMessage(error.message, "error");
    return;
  }
  $("#camera-button").addEventListener("click", async () => {
    try { await startCamera(); } catch (error) { setMessage(error.message, "error"); }
  });
  $("#signer-id").addEventListener("change", loadProgress);
  $("#session-number").addEventListener("change", loadProgress);
  $("#record-one").addEventListener("click", recordOne);
  $("#record-session").addEventListener("click", recordCurrentSession);
  $("#stop-auto").addEventListener("click", () => { state.stopRequested = true; });
  $("#delete-recording").addEventListener("click", deleteSelected);
  window.addEventListener("beforeunload", () => state.stream?.getTracks().forEach((track) => track.stop()));
}

initialize();
