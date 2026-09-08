<template>
  <div class="modal" v-if="show">
    <div class="wrap" @click="close">
      <div class="box">
        <div class="modal-box__header" @click.stop="">预览</div>
        <div class="preview-body" style="max-height: 70vh; overflow: auto">
          <div class="preview-container"></div>
        </div>
        <div class="modal-box__footer">
          <button class="primary" @click="close">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from "vue";
// let $ = require("jquery");
export default {
  name: "start-preview",
  setup() {
    const show = ref(false);
    const close = () => {
      show.value = false;
    };
    const showModal = (...html) => {
      console.log('打印预览')
      show.value = true;
      do {
        setTimeout(() => {
          // eslint-disable-next-line no-undef
          $(".preview-container").empty();
          // eslint-disable-next-line no-undef
          $(".preview-container").html(html);
        }, 200);
        return;
      } while ($(".container").length <= 0);
    };
    return {
      show,
      close,
      showModal,
    };
  },
};
</script>

<style>
/* 不同模板 间隙 */
.preview-container .hiprint-printTemplate {
  background: #fff;
  border-bottom: 10px solid #ccc;
}
/* 批量打印 间隙 */
.preview-container .hiprint-printTemplate .hiprint-printPanel:not(:last-of-type) {
  border-bottom: 5px solid #ccc;
}
</style>
<style scoped>
.preview-body {
  background: #ccc;
  padding: 14px 0;
  display: flex;
  justify-content: center;
}


/* modal */
.modal {
  padding: 0;
  margin: 0;
}
.modal .mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1000;
  height: 100%;
  background-color: #00000073;
}
.modal .wrap {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 3000;
  overflow: auto;
  background-color: #00000073;
  outline: 0;
}
.modal .wrap .box {
  position: relative;
  margin: 5% auto;
  width: 60%;
  background: #fff;
  border-radius: 4px;
  z-index: 3001;
  box-shadow: 0 5px 10px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
}
.modal-box__header {
  padding: 10px 14px;
  border-bottom: 1px solid #e9e9e9;
}
.modal-box__footer {
  text-align: end;
}
.modal-box__footer button {
  min-width: 100px;
}
.modal-box__footer button:not(:last-child) {
  margin-right: 10px;
}
</style>
