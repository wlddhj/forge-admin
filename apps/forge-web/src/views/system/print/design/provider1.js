import { hiprint } from "vue-plugin-hiprint";
export const provider1 = function (options) {
  console.log(options);
  var addElementTypes = function (context) {
    context.removePrintElementTypes("providerModule1");
    context.addPrintElementTypes("providerModule1", [
      new hiprint.PrintElementTypeGroup("常规", [
        {
          tid: "providerModule1.header",
          title: "单据表头",
          data: "单据表头",
          type: "text",
          options: {
            testData: "单据表头",
            height: 17,
            fontSize: 16.5,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
          },
        },
        {
          tid: "providerModule1.type",
          title: "单据类型",
          data: "单据类型",
          type: "text",
          options: {
            testData: "单据类型",
            height: 16,
            fontSize: 15,
            fontWeight: "700",
            textAlign: "center",
            hideTitle: true,
          },
        },
        {
          tid: "providerModule1.orderNo",
          title: "No",
          data: "XS888888888",
          type: "text",
          options: {
            field: "buyOrderNo",
            testData: "XS888888888",
            height: 16,
            width: 140,
            fontSize: 12,
            fontWeight: "600",
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "providerModule1.order",
          title: "订单编号",
          data: "XS888888888",
          type: "text",
          options: {
            field: "order",
            testData: "XS888888888",
            height: 16,
            fontSize: 6.75,
            fontWeight: "700",
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "providerModule1.date",
          title: "开单日期",
          data: "2020-01-01",
          type: "text",
          options: {
            field: "orderDate",
            testData: "2020-01-01",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
            formatter: "function(title,value,options,templateData,target){\nreturn formatDateJs(value)\n}"
          },
        },
        {
          tid: "providerModule1.barcode",
          title: "条形码",
          data: "XS888888888",
          type: "text",
          options: {
            field: "barcode",
            testData: "XS888888888",
            height: 32,
            fontSize: 12,
            lineHeight: 18,
            textAlign: "left",
            textType: "barcode",
          },
        },
        {
          tid: "providerModule1.qrcode",
          title: "二维码",
          data: "XS888888888",
          type: "text",
          options: {
            field: "qrcode",
            testData: "XS888888888",
            height: 80,
            width: 80,
            fontSize: 12,
            lineHeight: 18,
            textType: "qrcode",
          },
        },
        {
          tid: "providerModule1.saleOrderNo",
          title: "订货单号",
          type: "text",
          options: {
            field: "saleOrderNo",
            testData: "SJ2412130065",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "providerModule1.warehouseName",
          title: "出货仓库",
          type: "text",
          options: {
            field: "warehouseName",
            testData: "扬州众犇仓",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        { tid: "providerModule1.image", field:"logoTest", title: "Logo", data: "", type: "image" },
      ]),
      new hiprint.PrintElementTypeGroup("客户", [
        {
          tid: "providerModule1.customerName",
          title: "客户名称",
          type: "text",
          options: {
            field: "customerName",
            testData: "高级客户",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
            "fields": [
              {
                "text": "id",
                "field": "id"
              },
              {
                "text": "客户名称",
                "field": "customerName"
              }
            ]
          },
        },
        {
          tid: "providerModule1.customerCode",
          title: "客户代码",
          data: "ZB00201",
          type: "text",
          options: {
            field: "customerCode",
            testData: "18888888888",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "providerModule1.customerAddress",
          title: "地址",
          type: "text",
          options: {
            field: "customerAddress",
            testData: "上海",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "providerModule1.customerMobile",
          title: "电话",
          data: "18888888888",
          type: "text",
          options: {
            field: "customerMobile",
            testData: "18888888888",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
        {
          tid: "providerModule1.customerContacts",
          title: "联系人",
          type: "text",
          options: {
            field: "customerContacts",
            testData: "张三",
            height: 16,
            textAlign: "left",
            textContentVerticalAlign: "middle",
          },
        },
      ]),
    ]);
  };
  return {
    addElementTypes: addElementTypes,
  };
};
