import React, { useState, useEffect, useCallback } from 'react';
import { observer } from 'mobx-react-lite';
import { axios } from '@choerodon/boot';
import { Form, Select, TextField, Modal, SelectBox, Button, Password } from 'choerodon-ui/pro';
import _ from 'lodash';
import { Icon, Spin, Tooltip, Divider } from 'choerodon-ui';
import { Base64 } from 'js-base64';
import Tips from '../../../../../../components/new-tips';
import YamlEditor from '../../../../../../components/yamlEditor';
import emptyImg from '../../../../../../components/empty-page/image/owner.png';
import DependRepo from './DependRepo';
import { useAddTaskStore } from './stores';

import './index.less';

const { Option } = Select;

let currentSize = 10;

const originBranchs = [{
  value: 'master',
  name: 'master',
}, {
  value: 'feature',
  name: 'feature',
}, {
  value: 'bugfix',
  name: 'bugfix',
}, {
  value: 'hotfix',
  name: 'hotfix',
}, {
  value: 'release',
  name: 'release',
}, {
  value: 'tag',
  name: 'tag',
}];

const obj = {
  Maven: 'Maven构建',
  npm: 'Npm构建',
  // upload: '上传软件包至存储库',
  docker: 'Docker构建',
  chart: 'Chart构建',
  go: 'Go语言构建',
  maven_deploy: 'Maven发布',
};

const checkField = {
  upload: ['uploadFilePattern', 'uploadArtifactFileName'],
  docker: ['dockerContextDir', 'dockerFilePath'],
};

const AddTask = observer(() => {
  const {
    AddTaskFormDataSet,
    AddTaskStepFormDataSet,
    DependRepoDataSet,
    modal,
    handleOk,
    AddTaskUseStore: useStore,
    AppState: {
      currentMenuType: {
        id,
      },
    },
    jobDetail,
    appServiceId,
    PipelineCreateFormDataSet,
    image,
    intl: { formatMessage },
  } = useAddTaskStore();

  const [steps, setSteps] = useState([]);
  const [testConnect, setTestConnect] = useState('');
  const [ConnectLoading, setConnectLoading] = useState(false);
  const [customYaml, setCustomYaml] = useState(useStore.getYaml.custom);
  const [defaultImage, setDefaultImage] = useState('');
  const [expandIf, setExpandIf] = useState(false);
  const [expandIfSetting, setExpandIfSetting] = useState(false);
  const [branchsList, setBranchsList] = useState(originBranchs);

  useEffect(() => {
    if (steps.length > 0) {
      const old = AddTaskFormDataSet.current.get('private');
      if (steps.find(s => s.checked).repo) {
        const item = [...(steps.find(s => s.checked).repo.publicRepo || []), ...(steps.find(s => s.checked).repo.privateRepo || [])];
        AddTaskFormDataSet.current.set('private', item.length > 0 ? Array.from(new Set([...old, 'custom'])) : old.filter(o => o !== 'custom'));
      }
      if (steps.find(s => s.checked).mavenSettings) {
        AddTaskFormDataSet.current.set('private', Array.from(new Set([...old, 'copy'])));
      }
      AddTaskFormDataSet.getField('uploadFilePattern').set('required', steps.some(s => s.type === 'upload'));
      AddTaskFormDataSet.getField('dockerContextDir').set('required', steps.some(s => s.type === 'docker'));
      AddTaskFormDataSet.getField('dockerFilePath').set('required', steps.some(s => s.type === 'docker'));
      AddTaskFormDataSet.getField('uploadArtifactFileName').set('required', steps.some(s => s.type === 'upload'));
    }
  }, [steps]);

  function decode(base64) {
    const decodeStr = atob(base64);
    return decodeURI(decodeStr);
  }
  const getBranchsList = useCallback(async () => {
    const url = `devops/v1/projects/${id}/app_service/${PipelineCreateFormDataSet.current.get('appServiceId')}/git/page_branch_by_options?page=1&size=${currentSize}`;
    const res = await axios.post(url);
    if (res.content.length % 10 === 0 && res.content.length !== 0) {
      res.content.push({
        name: '加载更多',
        value: 'more',
      });
    }
    setBranchsList(res.content.map((c) => {
      if (c.branchName) {
        c.name = c.branchName;
        c.value = c.branchName;
      }
      return c;
    }));
  }, [currentSize]);

  useEffect(() => {
    async function initBranchs() {
      const value = AddTaskFormDataSet.current.get('triggerType');
      if (value && !value.includes('exact')) {
        setBranchsList(originBranchs);
      } else {
        getBranchsList();
      }
    }
    initBranchs();
  }, [AddTaskFormDataSet.current.get('triggerType')]);

  useEffect(() => {
    const init = async () => {
      const res = await useStore.axiosGetDefaultImage();
      useStore.setDefaultImage(res);
      if (jobDetail) {
        if (!['custom', 'chart'].includes(jobDetail.type)) {
          const { config, authType, username, token, password, sonarUrl, configType } = JSON.parse(jobDetail.metadata.replace(/'/g, '"'));
          let uploadFilePattern;
          let dockerContextDir;
          let dockerFilePath;
          let uploadArtifactFileName;
          let dockerArtifactFileName;
          let skipDockerTlsVerify;
          const share = [];
          let nexusMavenRepoIds;
          let zpk;
          config && config.forEach((c) => {
            if (c.type === 'upload') {
              uploadFilePattern = c.uploadFilePattern;
              uploadArtifactFileName = c.artifactFileName;
            } else if (c.type === 'docker') {
              dockerContextDir = c.dockerContextDir;
              dockerFilePath = c.dockerFilePath;
              skipDockerTlsVerify = c.skipDockerTlsVerify;
              dockerArtifactFileName = c.artifactFileName;
            } else if (c.type === 'Maven') {
              if (c.nexusMavenRepoIds) {
                nexusMavenRepoIds = c.nexusMavenRepoIds;
              }
            } else if (c.type === 'maven_deploy') {
              if (c.mavenDeployRepoSettings) {
                zpk = c.mavenDeployRepoSettings.nexusRepoIds;
                nexusMavenRepoIds = c.nexusMavenRepoIds;
              }
            }
            if (c.mavenSettings) {
              c.mavenSettings = Base64.decode(c.mavenSettings);
            }
          });
          ['toUpload', 'toDownload'].forEach(item => {
            if (jobDetail[item]) {
              share.push(item);
            }
          });
          const newSteps = config || [];
          const data = {
            ...jobDetail,
            uploadFilePattern,
            dockerContextDir,
            dockerFilePath,
            uploadArtifactFileName,
            dockerArtifactFileName,
            nexusMavenRepoIds,
            zpk,
            skipDockerTlsVerify,
            triggerValue: jobDetail.triggerValue && jobDetail.triggerType !== 'regex' ? jobDetail.triggerValue.split(',') : jobDetail.triggerValue,
            configType,
            // triggerRefs: jobDetail.triggerRefs ? jobDetail.triggerRefs.split(',') : [],
            glyyfw: appServiceId || PipelineCreateFormDataSet.getField('appServiceId').getText(PipelineCreateFormDataSet.current.get('appServiceId')),
            bzmc: newSteps.find(s => s.checked) ? newSteps.find(s => s.checked).name : '',
            authType,
            username,
            token,
            password,
            sonarUrl,
            private: newSteps.length > 0 && newSteps.find(s => s.checked).repos ? ['custom'] : '',
            share,
          };
          AddTaskFormDataSet.loadData([data]);

          setSteps(newSteps);
        } else {
          AddTaskFormDataSet.loadData(
            [
              {
                ...jobDetail,
                triggerValue: jobDetail.triggerValue && jobDetail.triggerType !== 'regex' ? jobDetail.triggerValue.split(',') : jobDetail.triggerValue,
                glyyfw: appServiceId || PipelineCreateFormDataSet.getField('appServiceId').getText(PipelineCreateFormDataSet.current.get('appServiceId')),
              },
            ]
          );
          if (jobDetail.type === 'custom') {
            setCustomYaml(jobDetail.metadata);
          }
        }
        if (!jobDetail.image) {
          AddTaskFormDataSet.current.set('selectImage', '0');
          if (!image) {
            AddTaskFormDataSet.current.set('image', useStore.getDefaultImage);
            setDefaultImage(useStore.getDefaultImage);
          } else {
            AddTaskFormDataSet.current.set('image', image);
            setDefaultImage(image);
          }
        } else {
          AddTaskFormDataSet.current.set('selectImage', '1');
          AddTaskFormDataSet.current.set('image', jobDetail.image);
          setDefaultImage(jobDetail.image);
        }
        // if (jobDetail.image !== res) {
        //   AddTaskFormDataSet.current.set('selectImage', '1');
        // } else {
        //   AddTaskFormDataSet.current.set('selectImage', '0');
        // }
      } else {
        if (image) {
          AddTaskFormDataSet.current.set('selectImage', '0');
          AddTaskFormDataSet.current.set('image', image);
          setDefaultImage(image);
        } else {
          AddTaskFormDataSet.current.set('selectImage', '0');
          AddTaskFormDataSet.current.set('image', useStore.getDefaultImage);
          setDefaultImage(useStore.getDefaultImage);
        }
        AddTaskFormDataSet.current.set('glyyfw', appServiceId || PipelineCreateFormDataSet.getField('appServiceId').getText(PipelineCreateFormDataSet.current.get('appServiceId')));
      }
    };
    init();
  }, []);

  function encode(str) {
    const encodeStr = encodeURI(str);
    return btoa(encodeStr);
  }

  const handleAdd = async () => {
    const result = await AddTaskFormDataSet.validate();
    if (result) {
      if (AddTaskFormDataSet.current.get('type') === 'sonar' && AddTaskFormDataSet.current.get('configType') === 'custom') {
        const connet = await handleTestConnect();
        if (!connet) {
          return false;
        }
      }
      let data = AddTaskFormDataSet.toData()[0];
      data = {
        ...data,
        // eslint-disable-next-line no-nested-ternary
        triggerValue: data.triggerValue && data.triggerType !== 'regex' ? (typeof data.triggerValue === 'object' ? data.triggerValue.join(',') : data.triggerValue) : data.triggerValue,
        image: data.selectImage === '1' ? data.image : null,

        toUpload: data.type === 'build' && data.share.includes('toUpload'),
        toDownload: data.type === 'build' && data.share.includes('toDownload'),

        metadata: (function () {
          if (data.type === 'build') {
            return JSON.stringify({
              config: steps.map((s, sIndex) => {
                s.sequence = sIndex;
                s.script = s.yaml;
                if (s.repo) {
                  s.repos = [...(s.repo.publicRepo || []).map(p => {
                    p.private = p.privateIf;
                    return p;
                  }), ...(s.repo.privateRepo || []).map(p => {
                    p.private = p.privateIf;
                    return p;
                  })];
                }
                if (data.nexusMavenRepoIds && s.type === 'Maven') {
                  s.nexusMavenRepoIds = data.nexusMavenRepoIds;
                }
                if (s.mavenSettings) {
                  s.mavenSettings = Base64.encode(s.mavenSettings);
                }
                if (s.type === 'upload') {
                  s.uploadFilePattern = data.uploadFilePattern;
                  if (data.uploadArtifactFileName) {
                    s.artifactFileName = data.uploadArtifactFileName;
                  }
                }
                if (s.type === 'docker') {
                  s.dockerContextDir = data.dockerContextDir;
                  s.dockerFilePath = data.dockerFilePath;
                  s.skipDockerTlsVerify = data.skipDockerTlsVerify;
                  if (data.dockerArtifactFileName) {
                    s.artifactFileName = data.dockerArtifactFileName;
                  }
                }
                if (data.zpk && s.type === 'maven_deploy') {
                  s.mavenDeployRepoSettings = {
                    nexusRepoIds: data.zpk,
                  };
                  s.nexusMavenRepoIds = data.nexusMavenRepoIds;
                }
                return s;
              }),
            }).replace(/"/g, "'");
          } else if (data.type === 'sonar') {
            return JSON.stringify({
              ...data,
              metadata: '',
            }).replace(/"/g, "'");
          } else if (data.type === 'custom') {
            return customYaml;
          }
        }()),
      };
      handleOk(data);
      return true;
    } else {
      let checkedIndex;
      for (let i = 0; i < steps.length; i++) {
        if (Object.keys(checkField).includes(steps[i].type)) {
          for (let j = 0; j < checkField[steps[i].type].length; j++) {
            const isValid = AddTaskFormDataSet.current.getField(checkField[steps[i].type][j]).isValid();
            if (!isValid) {
              checkedIndex = i;
              break;
            }
          }
        }
        if (String(checkedIndex) !== 'undefined') {
          break;
        }
      }
      if (String(checkedIndex) !== 'undefined') {
        setSteps(steps.map((s, sIndex) => {
          if (String(sIndex) === String(checkedIndex)) {
            s.checked = true;
          } else {
            s.checked = false;
          }
          return s;
        }));
      }
      return false;
    }
  };

  useEffect(() => {
    async function useEffectByType() {
      if (AddTaskFormDataSet.current.get('type') === 'sonar') {
        if (AddTaskFormDataSet.current.get('authType') === 'username') {
          modal.update({
            okProps: {
              disabled: !testConnect,
            },
          });
        } else {
          modal.update({
            okProps: {
              disabled: false,
            },
          });
        }
        await useStore.axiosGetHasDefaultSonar();
        modal.update({
          okProps: {
            disabled: !useStore.getHasDefaultSonar,
          },
        });
      } else {
        modal.update({
          okProps: {
            disabled: false,
          },
        });
      }
      if (AddTaskFormDataSet.current.get('type') !== 'build') {
        AddTaskFormDataSet.getField('uploadFilePattern').set('required', false);
        AddTaskFormDataSet.getField('dockerContextDir').set('required', false);
        AddTaskFormDataSet.getField('dockerFilePath').set('required', false);
        AddTaskFormDataSet.getField('uploadArtifactFileName').set('required', false);
      }
      if (AddTaskFormDataSet.current.get('type') === 'custom') {
        AddTaskFormDataSet.getField('name').set('required', false);
        AddTaskFormDataSet.getField('glyyfw').set('required', false);
      }
    }

    useEffectByType();
  }, [testConnect, AddTaskFormDataSet.current.get('type'), AddTaskFormDataSet.current.get('authType')]);

  modal.handleOk(handleAdd);

  const handleClickStepItem = (index) => {
    setSteps(steps.map((s, sIndex) => {
      if (sIndex === index) {
        s.checked = true;
        AddTaskFormDataSet.current.set('bzmc', s.name);
      } else {
        s.checked = false;
      }
      return s;
    }));
  };

  const renderer = ({ text }) => text;

  const optionRenderer = ({ text }) => {
    if (text === 'Docker构建') {
      return (
        <Tooltip title="由于该步骤中Dockerfile内kaniko指令限制，建议此步骤作为同任务中最后一个步骤。">
          {text}<Icon style={{ position: 'relative', left: '1px', bottom: '1px' }} type="help" />
        </Tooltip>
      );
    }
    return text;
  };

  const handleAddStepItem = (index) => {
    Modal.open({
      key: Modal.key(),
      title: '添加步骤',
      style: {
        width: 380,
      },
      children: (
        <Form dataSet={AddTaskStepFormDataSet}>
          <Select
            onOption={({ record }) => ({
              disabled: steps.map(s => s.type).includes(record.get('value')),
            })}
            optionRenderer={optionRenderer}
            renderer={renderer}
            name="kybz"
          >
            {/* <Option value="Maven">Maven构建</Option> */}
            {/* <Option value="npm">Npm构建</Option> */}
            {/* /!* <Option value="go">Go语言构建</Option> *!/ */}
            {/* <Option value="upload">上传软件包至存储库</Option> */}
            {/* <Option value="docker">Docker构建 */}
            {/*   <Tooltip title="由于该步骤中Dockerfile内kaniko指令限制，建议此步骤作为同任务中最后一个步骤。"> */}
            {/*    <Icon style={{ position: 'relative', left: '1px', bottom: '1px' }} type="help" /> */}
            {/*   </Tooltip> */}
            {/* </Option> */}
            {/* <Option value="chart">Chart构建</Option> */}
          </Select>
        </Form>
      ),
      drawer: true,
      okText: '添加',
      onOk: async () => {
        const result = await AddTaskStepFormDataSet.validate();
        if (!result) {
          return false;
        }
        if (AddTaskStepFormDataSet.current && AddTaskStepFormDataSet.current.get('kybz')) {
          const value = AddTaskStepFormDataSet.current.get('kybz');
          if (value) {
            const newSteps = steps;
            newSteps.splice(index, 0, {
              name: obj[value],
              type: value,
              checked: true,
              yaml: useStore.getYaml[value] || '',
            });
            setSteps(newSteps.map((s, sIndex) => {
              if (sIndex === index) {
                s.checked = true;
                AddTaskFormDataSet.current.set('bzmc', s.name);
              } else {
                s.checked = false;
              }
              return s;
            }));
          }
        }
        AddTaskStepFormDataSet.reset();
      },
      onCancel: () => {
        AddTaskStepFormDataSet.reset();
      },
    });
  };

  const handleDeleteStep = (index, e) => {
    e.stopPropagation();
    const newSteps = steps;
    newSteps.splice(index, 1);
    const newSteps2 = newSteps.map((s, sIndex) => {
      if (index === 0) {
        if (sIndex === 0) {
          s.checked = true;
          AddTaskFormDataSet.current.set('bzmc', s.name);
        } else {
          s.checked = false;
        }
      } else if (sIndex === (index - 1)) {
        s.checked = true;
        AddTaskFormDataSet.current.set('bzmc', s.name);
      } else {
        s.checked = false;
      }
      return s;
    });
    setSteps(newSteps2);
  };

  const generateSteps = () => (
    <div className="AddTask_stepItemsContainer">
      {
        steps.length > 0 ? steps.map((s, index) => (
          <div className="AddTask_stepMapContent">
            <div style={{ display: index === 0 ? 'flex' : 'none' }} className="AddTask_stepAdd">
              <span onClick={() => handleAddStepItem(index)} style={{ fontSize: 20 }}>+</span>
            </div>
            <div className="AddTask_addLine" />
            <div onClick={() => handleClickStepItem(index)} className={s.checked ? 'AddTask_stepItem AddTask_stepItemChecked' : 'AddTask_stepItem'}>
              {s.name}
              <Icon onClick={(e) => handleDeleteStep(index, e)} style={{ position: 'relative', bottom: '1px' }} type="delete_forever" />
            </div>
            <div className="AddTask_addLine" />
            <div className="AddTask_stepAdd">
              <span onClick={() => handleAddStepItem(index + 1)} style={{ fontSize: 20 }}>+</span>
            </div>
          </div>
        )) : (
          <div className="AddTask_stepMapContent">
            <div className="AddTask_stepAdd">
              <span onClick={() => handleAddStepItem(0)} style={{ fontSize: 20 }}>+</span>
            </div>
          </div>
        )
      }
    </div>
  );

  const handleChangeValue = (value, index) => {
    setSteps(steps.map((s, sIndex) => {
      if (s.checked) {
        s.yaml = value;
      }
      return s;
    }));
  };

  const handleTestConnect = () => new Promise((resolve) => {
    setConnectLoading(true);
    const data = AddTaskFormDataSet.current.toData();
    useStore.axiosConnectTest(data, id).then((res) => {
      setTestConnect(res);
      setConnectLoading(false);
      resolve(res);
    });
  });

  const handleChangeBuildTemple = (value) => {
    if (value) {
      AddTaskFormDataSet.current.set('bzmc', obj[value]);
      const origin = value !== 'go' ? [{
        name: obj[value],
        type: value,
        checked: true,
        yaml: useStore.getYaml[value],
      }] : [];
      let extra = [];
      if (value === 'Maven') {
        extra = [
          //   {
          //   name: '上传软件包至制品库',
          //   type: 'maven_deploy',
          //   checked: false,
          //   yaml: useStore.getYaml.maven_deploy,
          // },
          {
            name: 'Docker构建',
            type: 'docker',
            checked: false,
          }];
      } else if (value === 'npm') {
        extra = [{
          name: 'Docker构建',
          type: 'docker',
          checked: false,
        }];
      } else if (value === 'go') {
        extra = [{
          name: 'Docker构建',
          type: 'docker',
          checked: true,
        }];
      }
      setSteps([...origin, ...extra]);
    } else {
      setSteps([]);
    }
  };

  const renderTestConnect = () => {
    function renderDom() {
      if (String(testConnect)) {
        if (testConnect) {
          return (
            <React.Fragment>
              <div className="addTask_testConnect_success"><i className="success" /></div>
              <p className="addTask_testConnect_havnot" style={{ marginTop: '4px', marginBottom: '6px' }}>测试连接: <span>成功</span></p>
              <p style={{ color: 'rgba(58,52,95,0.65)' }}>(重新进行连接测试: <Button onClick={handleTestConnect} funcType="flat" style={{ width: 'auto', color: '#3F51B5' }}>测试连接</Button>)</p>
            </React.Fragment>
          );
        } else {
          return (
            <React.Fragment>
              <div style={{ borderColor: 'rgb(247, 122, 112)' }} className="addTask_testConnect_success"><i className="failure">X</i></div>
              <p className="addTask_testConnect_havnot" style={{ marginTop: '4px', marginBottom: '6px' }}>测试连接: <span style={{ color: 'rgb(247, 122, 112)' }}>失败</span></p>
              <p style={{ color: 'rgba(58,52,95,0.65)' }}>(重新进行连接测试: <Button onClick={handleTestConnect} funcType="flat" style={{ width: 'auto', color: '#3F51B5' }}>测试连接</Button>)</p>
            </React.Fragment>
          );
        }
      } else {
        return (
          <React.Fragment>
            <img style={{ width: 121, marginRight: 39 }} src={emptyImg} alt="none" />
            <div>
              <p className="addTask_testConnect_havnot">未进行过连接</p>
              <Button className="addTest_notTestButton" onClick={handleTestConnect} funcType="raised" style={{ width: 'auto', color: '#3F51B5' }} newLine>测试连接</Button>
            </div>
          </React.Fragment>
        );
      }
    }
    if ((AddTaskFormDataSet.current.get('authType') === 'username')) {
      return (
        <div
          colSpan={4}
          newLine
          className="addTask_testConnect_container"
          style={{
            background: ConnectLoading ? 'rgba(117,137,242,0.06)' : (function () {
              if (String(testConnect)) {
                return 'rgba(0,191,165,0.06)';
              } else {
                return 'white';
              }
            }()),
            flexDirection: !String(testConnect) && !ConnectLoading ? 'row' : 'column',
            textAlign: 'center',
          }}
        >
          {
            ConnectLoading ? (
              <React.Fragment>
                <Spin size="large" />
                <p className="addTask_testConnect_havnot">正在进行连接测试</p>
              </React.Fragment>
            ) : renderDom()
          }
        </div>
      );
    }
  };

  const handleAddRepo = (data, privateIf) => {
    const old = AddTaskFormDataSet.current.get('private');
    let flag = false;
    if (data.length === 0) {
      flag = true;
      const newData = old.filter(o => o !== 'custom');
      AddTaskFormDataSet.current.set('private', newData);
    }
    const data2 = steps.map(s => {
      if (s.checked) {
        const newRepo = flag ? undefined : {
          privateRepo: [],
          publicRepo: [],
        };
        data.forEach(d => {
          if (d.privateIf) {
            newRepo.privateRepo = [
              ...newRepo.privateRepo,
              d,
            ];
          } else {
            newRepo.publicRepo = [
              ...newRepo.publicRepo,
              d,
            ];
          }
        });
        s.repo = {
          ...s.repo,
          ...newRepo,
        };
      }
      return s;
    });
    setSteps(data2);
  };

  const handleOpenRepo = () => {
    if (AddTaskFormDataSet.current.get('private').includes('copy')) {
      Modal.confirm({
        title: '切换配置方式',
        children: '确定要切换为"界面可视化定义"的方式吗，切换后，将会清空已有的Setting配置。',
      }).then(button => {
        if (button === 'ok') {
          setSteps(steps.map(s => {
            if (s.checked) {
              s.mavenSettings = '';
            }
            return s;
          }));
          AddTaskFormDataSet.current.set('private', ['custom']);
          initRepo();
        } else {
          AddTaskFormDataSet.current.set('private', ['copy']);
        }
      });
    } else {
      initRepo();
    }
    function initRepo() {
      Modal.open({
        key: Modal.key(),
        title: '配置依赖仓库',
        style: {
          width: 380,
        },
        children: <DependRepo handleParentCancel={handleCancel} dsData={steps.find(s => s.checked).repo} handleAdd={handleAddRepo} ds={DependRepoDataSet} />,
        drawer: true,
        okText: '添加',
      });
    }
  };

  const handleCancel = (data) => {
    const old = AddTaskFormDataSet.current.get('private');
    if (data.length === 0) {
      const newData = old.filter(o => o !== 'custom');
      AddTaskFormDataSet.current.set('private', newData);
    }
  };

  const handleOpenXML = () => {
    if (AddTaskFormDataSet.current.get('private').includes('custom')) {
      Modal.confirm({
        title: '切换配置方式',
        children: '确定要切换为"粘贴XML内容"的方式吗，切换后，将会清空已有的Setting配置。',
      }).then(button => {
        if (button === 'ok') {
          setSteps(steps.map(s => {
            if (s.checked) {
              s.repo = undefined;
            }
            return s;
          }));
          AddTaskFormDataSet.current.set('private', ['copy']);
          initXml();
        } else {
          AddTaskFormDataSet.current.set('private', ['custom']);
        }
      });
    } else {
      initXml();
    }
    function initXml() {
      const originMavenSetting = steps.find(s => s.checked).mavenSettings || '';
      Modal.open({
        key: Modal.key(),
        title: '配置依赖仓库',
        style: {
          width: 380,
        },
        drawer: true,
        children: (
          <div>
            <p>Setting文件内容</p>
            <YamlEditor
              readOnly={false}
              colSpan={4}
              newLine
              value={steps.length > 0 ? steps.find(s => s.checked).mavenSettings || '' : ''}
              onValueChange={(valueYaml) => setSteps(steps.map(s => {
                if (s.checked) {
                  s.mavenSettings = valueYaml;
                }
                return s;
              }))}
              modeChange={false}
              showError={false}
            />
          </div>
        ),
        onOk: () => {
          if (!steps.find(s => s.checked).mavenSettings) {
            AddTaskFormDataSet.current.set('private', AddTaskFormDataSet.current.get('private').filter(o => o !== 'copy'));
          }
        },
        onCancel: () => {
          setSteps(steps.map(s => {
            if (s.checked) {
              s.mavenSettings = originMavenSetting;
            }
            return s;
          }));
          if (!steps.find(s => s.checked).mavenSettings) {
            AddTaskFormDataSet.current.set('private', AddTaskFormDataSet.current.get('private').filter(o => o !== 'copy'));
          }
        },
      });
    }
  };

  const handleChangePrivate = (newV, oldV) => {
    newV = newV || [];
    oldV = oldV || [];
    function minus(a, b) {
      return [...a.filter(item => !b.includes(item)), ...b.filter(item => !a.includes(item))];
    }
    const extra = minus(newV, oldV)[0];
    if (newV.length > oldV.length) {
      // 打钩
      if (extra === 'custom') {
        handleOpenRepo();
      } else {
        handleOpenXML();
      }
    } else if (extra === 'custom') {
      Modal.confirm({
        title: '清空配置',
        children: '确定清空已有的Setting配置吗?',
      }).then(button => {
        if (button === 'ok') {
          setSteps(steps.map(s => {
            if (s.checked) {
              s.repo = undefined;
            }
            return s;
          }));
        } else {
          AddTaskFormDataSet.current.set('private', extra);
        }
      });
    } else {
      Modal.confirm({
        title: '清空配置',
        children: '确定清空已有的Setting配置吗?',
      }).then(button => {
        if (button === 'ok') {
          setSteps(steps.map(s => {
            if (s.checked) {
              s.mavenSettings = '';
            }
            return s;
          }));
        } else {
          AddTaskFormDataSet.current.set('private', ['copy']);
        }
      });
    }
    // if (newV.length > oldV.length) {
    //   //  打钩
    //   Modal.open({
    //     key: Modal.key(),
    //     title: `修改${extra === 'true' ? '私有' : '公有'}依赖仓库`,
    //     style: {
    //       width: 380,
    //     },
    //     children: <DependRepo handleParentCancel={handleCancel} handleAdd={handleAddRepo} ds={DependRepoDataSet} privateIf={extra === 'true'} />,
    //     drawer: true,
    //     okText: '添加',
    //   });
    // } else {
    // }
  };

  const renderderBranchs = ({ text }) => (text === '加载更多' ? (
    <a
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        currentSize += 10;
        getBranchsList();
      }}
    >{text}</a>
  ) : text);

  const getMissionOther = () => {
    if (AddTaskFormDataSet.current.get('type') === 'build') {
      return [
        <div colSpan={4} className="AddTask_configStep">
          <p>配置步骤</p>
        </div>,
        <Select onChange={handleChangeBuildTemple} name="gjmb">
          <Option value="Maven">Maven模板</Option>
          <Option value="npm">Npm模板</Option>
          <Option value="go">Go模板</Option>
        </Select>,
        <div newLine colSpan={4} style={{ display: 'flex', flexDirection: 'column' }} className="AddTask_stepContent">
          {generateSteps()}
          <div
            className="stepformContent"
          >
            <TextField
              onChange={(value) => {
                setSteps(steps.map(s => {
                  if (s.checked) {
                    s.name = value;
                  }
                  return s;
                }));
              }}
              style={{
                width: 339,
                marginTop: 30,
                marginBottom: 20,
                marginRight: 8,
                display: steps.length === 0 ? 'none' : 'block',
              }}
              // newLine
              name="bzmc"
            />
            {
              (function () {
                if (steps.find(s => s.checked)) {
                  const type = steps.find(s => s.checked).type;
                  if (type === 'Maven') {
                    return (
                      <Select
                        name="nexusMavenRepoIds"
                        style={{
                          width: 339,
                          marginTop: 30,
                          marginBottom: 20,
                        }}
                        renderer={({ text }) => (
                          <Tooltip title={text}>
                            {text}
                          </Tooltip>
                        )}
                      />
                    );
                  } else if (type === 'maven_deploy') {
                    return (
                      <Select
                        name="zpk"
                        style={{
                          width: 339,
                          marginTop: 30,
                          marginBottom: 20,
                        }}
                      />
                    );
                  }
                }
              }())
            }
          </div>
          <div style={{ marginBottom: '20px' }}>
            {
              (function () {
                if (steps.find(s => s.checked)) {
                  const type = steps.find(s => s.checked).type;
                  if (type === 'maven_deploy') {
                    return (
                      <Select
                        name="nexusMavenRepoIds"
                        style={{
                          width: '100%',
                          // marginBottom: 20,
                        }}
                        help="123"
                        showHelp="tooltip"
                        renderer={({ text }) => (
                          <Tooltip title={text}>
                            {text}
                          </Tooltip>
                        )}
                      />
                    );
                  }
                }
              }())
            }
          </div>

          {
            steps.find(s => s.checked) && (steps.find(s => s.checked).type === 'Maven' || steps.find(s => s.checked).type === 'maven_deploy')
              ? [
                <div style={{
                  marginLeft: '-16px',
                  height: '1px',
                  width: 'calc(100% + 32px)',
                  background: '#d8d8d8',
                }}
                />,
                <div
                  colSpan={4}
                  newLine
                  className="advanced_text"
                  style={{
                    cursor: 'pointer',
                    // borderTop: '1px solid #d8d8d8',
                    paddingTop: '20px',
                  }}
                  onClick={() => setExpandIfSetting(!expandIfSetting)}
                >
                  高级设置<Icon style={{ fontSize: 18 }} type={expandIfSetting ? 'expand_less' : 'expand_more'} />
                </div>,
                expandIfSetting ? (
                  <div newLine>
                    <div className="c7ncd-pipeline-add-task-tips">
                      <span>Setting配置</span>
                      <Tooltip
                        title="用于运行过程中，在项目根目录下生成settings文件；您可在脚本中加上 -s 或者 -gs 参数进行使用"
                        theme="light"
                      >
                        <Icon type="help" />
                      </Tooltip>
                    </div>
                    <SelectBox
                      onChange={handleChangePrivate}
                      name="private"
                      style={{
                        width: 339,
                      }}
                      className="addTask_authType"
                    >
                      <Option value="custom">
                        <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                          自定义仓库配置
                          <Button
                            onClick={handleOpenRepo}
                            style={{
                              marginLeft: 8,
                              display: (function () {
                                const repo = steps.find(s => s.checked).repo;
                                if (JSON.stringify(repo) && JSON.stringify(repo) !== '{}') {
                                  return 'inline-block';
                                }
                                return 'none';
                              }()),
                            }}
                          >
                            <Icon
                              style={{
                                color: '#3F51B5',
                              }}
                              type="mode_edit"
                            />
                          </Button>
                        </span>
                      </Option>
                      {
                        steps.find(s => s.checked) && steps.find(s => s.checked).type !== 'maven_deploy' && (
                          <Option value="copy">
                            <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                              粘贴XML内容
                              <Button
                                onClick={handleOpenXML}
                                style={{
                                  marginLeft: 8,
                                  display: steps.find(s => s.checked).mavenSettings ? 'inline-block' : 'none',
                                }}
                              >
                                <Icon
                                  style={{
                                    color: '#3F51B5',
                                  }}
                                  type="mode_edit"
                                />
                              </Button>
                            </span>
                          </Option>
                        )
                      }
                    </SelectBox>
                  </div>
                ) : '',
                <div style={{ height: 20 }} />,
              ]
              : ''
          }
          {
            (function () {
              if (steps.length > 0) {
                const type = steps.find(s => s.checked).type;
                if (type && ['Maven', 'npm', 'go', 'maven_deploy'].includes(type)) {
                  return [
                    <div style={{
                      marginLeft: '-16px',
                      height: '1px',
                      width: 'calc(100% + 32px)',
                      background: '#d8d8d8',
                    }}
                    />,
                    (
                      <div
                        style={{
                          paddingTop: '20px',
                        }}
                      >
                        <YamlEditor
                          readOnly={false}
                          colSpan={4}
                          newLine
                          value={steps.length > 0 ? steps.find(s => s.checked).yaml : ''}
                          onValueChange={(valueYaml) => handleChangeValue(valueYaml)}
                          modeChange={false}
                          showError={false}
                        />
                      </div>
                    )];
                } else if (type === 'upload') {
                  return [
                    <TextField
                      style={{ width: 314 }}
                      addonAfter={<Tips helpText="此处定义的路径将用于存放构建所需的全部内容" />}
                      name="uploadFilePattern"
                    />,
                    <TextField style={{ width: 339, marginTop: 20, marginBottom: 20 }} name="uploadArtifactFileName" />,
                  ];
                } else if (type === 'docker') {
                  return [
                    <div style={{ marginBottom: 20 }}>
                      <TextField
                        style={{ width: 312 }}
                        name="dockerFilePath"
                        showHelp="tooltip"
                        help="Dockerfile路径为Dockerfile文件相对于代码库根目录所在路径，如docker/Dockerfile或Dockerfile"
                      />
                    </div>,
                    <div style={{ marginBottom: 20 }}>
                      <TextField
                        className="dockerContextDir"
                        style={{ width: 312 }}
                        name="dockerContextDir"
                        showHelp="tooltip"
                        help="ContextPath为docker build命令执行上下文路径。填写相对于代码根目录的路径，如docker"
                        onFocus={() => {
                          let res;
                          const value = AddTaskFormDataSet.current.get('dockerFilePath');
                          const arrValue = value.split('');
                          const lastIndex = _.findLastIndex(arrValue, (o) => o === '/');
                          if (lastIndex !== -1) {
                            res = arrValue.slice(0, lastIndex).join('');
                          } else {
                            res = '.';
                          }
                          AddTaskFormDataSet.current.set('dockerContextDir', res);
                        }}
                      />
                    </div>,
                    <div style={{ position: 'relative' }}>
                      <SelectBox
                        style={{
                          marginTop: 20,
                        }}
                        name="skipDockerTlsVerify"
                      >
                        <Option value>是</Option>
                        <Option value={false}>否</Option>
                      </SelectBox>
                      <Tooltip title="是否对harbor域名进行证书校验">
                        <Icon type="help" style={{ position: 'absolute', top: '1px', left: '83px' }} />
                      </Tooltip>
                    </div>,
                  ];
                }
              }
              return '';
            }())
          }
        </div>,
      ];
    } else {
      let extra;
      if (AddTaskFormDataSet.current.get('authType') === 'username') {
        extra = [
          <TextField newLine name="username" />,
          <Password name="password" />,
          <TextField name="sonarUrl" />,
        ];
      } else {
        extra = [
          <TextField newLine name="token" />,
          <TextField name="sonarUrl" />,
        ];
      }
      return [
        <SelectBox
          className="addTask_authType"
          name="configType"
          help={!useStore.getHasDefaultSonar ? '平台暂无默认的SonarQube配置，请在自定义配置中进行添加。' : ''}
        >
          <Option value="default">默认配置</Option>
          <Option value="custom">自定义配置</Option>
        </SelectBox>,
        AddTaskFormDataSet.current.get('configType') === 'custom' ? [
          <SelectBox className="addTask_authType" name="authType">
            <Option value="username">用户名与密码</Option>
            <Option value="token">Token</Option>
          </SelectBox>,
          ...extra,
          renderTestConnect(),
        ] : '',
      ];
    }
  };

  const handleChangeImage = (data) => {
    if (data === defaultImage) {
      AddTaskFormDataSet.current.set('selectImage', '0');
    } else {
      AddTaskFormDataSet.current.set('selectImage', '1');
    }
  };

  const getImageDom = () => [
    <div colSpan={4} newLine className="advanced_text border-advanced" style={{ cursor: 'pointer' }} onClick={() => setExpandIf(!expandIf)}>
      高级设置<Icon style={{ fontSize: 18 }} type={expandIf ? 'expand_less' : 'expand_more'} />
    </div>,
    expandIf ? (
      <Select
        // disabled={
        //     !!(AddTaskFormDataSet.current && AddTaskFormDataSet.current.get('selectImage') === '0')
        //   }
        showHelp="tooltip"
        help="CI任务Runner镜像是该CI任务的执行环境。您可直接使用此处给出的默认Runner镜像，或是输入自定义的CI任务Runner镜像"
        onChange={handleChangeImage}
        newLine
        combo
        colSpan={4}
        name="image"
      >
        <Option value={defaultImage}>{`${defaultImage}${defaultImage === useStore.getDefaultImage ? '(默认)' : ''}`}</Option>
      </Select>
    ) : '',
  ];

  const getShareSettings = () => (expandIf && AddTaskFormDataSet.current.get('type') === 'build' ? (
    <div newLine colSpan={4}>
      <Tips
        title={formatMessage({ id: 'c7ncd.pipelineManage.create.share.title' })}
        helpText={formatMessage({ id: 'c7ncd.pipelineManage.create.share.tips' })}
        newLine
      />
      <SelectBox name="share" newLine colSpan={4}>
        <Option value="toUpload">上传共享目录choerodon-ci-cache</Option>
        <Option value="toDownload">下载共享目录choerodon-ci-cache</Option>
      </SelectBox>
    </div>
  ) : null);

  function renderTriggerTypeTips() {
    const type = AddTaskFormDataSet.current.get('triggerType');
    switch (type) {
      case 'refs':
        return '您可在此选择或输入触发该任务的分支类型；支持多填多选；若不填写，则默认为所有分支和tag';
      case 'exact_match':
        return '您可在此精确选择或输入触发该任务的具体分支名称；支持多填多选；若不填写，则默认为所有分支和tag';
      default:
        return '您可在此选择或输入某几个具体的分支名称以此来精确排除；此处支持多填多选；若不填写，则默认为没有需要排除的分支或tag';
    }
  }

  return (
    <React.Fragment>
      <Form className="addTaskForm" dataSet={AddTaskFormDataSet} columns={4}>
        <Select name="type" colSpan={2}>
          <Option value="build">构建</Option>
          <Option value="sonar">代码检查</Option>
          <Option value="custom">自定义</Option>
          <Option value="chart">发布Chart</Option>
        </Select>
        {
          AddTaskFormDataSet.current.get('type') !== 'custom' ? [
            <TextField name="name" colSpan={2} />,
            <TextField name="glyyfw" colSpan={1} />,

            <div className="matchType" style={{ display: 'inline-flex', position: 'relative' }} colSpan={3}>
              <Select
                onChange={(value) => {
                  AddTaskFormDataSet.current.set('triggerValue', undefined);
                }}
                combo={false}
                style={{ marginRight: 8 }}
                name="triggerType"
                allowClear={false}
                clearButton={false}
              >
                <Option value="refs">分支类型匹配</Option>
                <Option value="regex">正则匹配</Option>
                <Option value="exact_match">精确匹配</Option>
                <Option value="exact_exclude">精确排除</Option>
              </Select>
              {AddTaskFormDataSet.current.get('triggerType') === 'regex' ? (
                <TextField
                  name="triggerValue"
                  showHelp="tooltip"
                  help="'您可在此输入正则表达式来配置触发分支；例：若想匹配以 feature 开头的分支，可以输入 ^feature.*。更多表达式，详见用户手册。若不填写，则默认为所有分支和tag'"
                />
              ) : (
                <Select
                  combo
                  searchable
                  multiple
                  name="triggerValue"
                  showHelp="tooltip"
                  help={renderTriggerTypeTips()}
                  searchMatcher="branchName"
                  optionRenderer={({ text }) => renderderBranchs({ text })}
                  maxTagCount={3}
                  maxTagPlaceholder={(omittedValues) => <Tooltip title={omittedValues.join(',')}>
                    {`+${omittedValues.length}`}
                  </Tooltip>}
                  className="addTaskForm-select"
                  renderer={renderderBranchs}
                  colSpan={2}
                >
                  {
                      branchsList.map(b => (
                        <Option value={b.value}>{b.name}</Option>
                      ))
                    }
                </Select>
              )}
            </div>,
            getImageDom(),
            getShareSettings(),
            AddTaskFormDataSet.current.get('type') !== 'chart' ? getMissionOther() : '',
          ] : [
            <YamlEditor
              readOnly={false}
              colSpan={4}
              newLine
              value={customYaml}
              onValueChange={(valueYaml) => setCustomYaml(valueYaml)}
              modeChange={false}
              showError={false}
            />,
          ]
        }
      </Form>
    </React.Fragment>
  );
});

export default AddTask;
